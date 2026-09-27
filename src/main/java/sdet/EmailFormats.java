package sdet;

import com.ibm.icu.text.IDNA;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public final class EmailFormats {
  private EmailFormats() {}

  public static boolean deterministic(String value) {
    return value.contains("@");
  }

  public static boolean generated(String value) {
    String address = value;
    int display = address.lastIndexOf(" <");
    if (display >= 0) {
      String rest = address.substring(display + 2).strip();
      if (!rest.endsWith(">")) return false;
      address = rest.substring(0, rest.length() - 1);
    }
    int at = address.lastIndexOf('@');
    if (at <= 0) return false;
    String local = address.substring(0, at), domain = address.substring(at + 1);
    if (local.getBytes(StandardCharsets.UTF_8).length > 64) return false;
    if (local.startsWith("\"") && local.endsWith("\"")) {
      int[] chars = local.substring(1, local.length() - 1).codePoints().toArray();
      if (chars.length == 0) return false;
      for (int i = 0; i < chars.length; i++) {
        int c = chars[i];
        if (c == '\\') {
          if (++i == chars.length || chars[i] < 0x21 || chars[i] > 0x7e) return false;
        } else if (!(c == ' '
            || c == '\t'
            || c == 0x21
            || c >= 0x23 && c <= 0x5b
            || c >= 0x5d && c <= 0x7e
            || legacy(c))) return false;
      }
    } else {
      for (String atom : local.split("\\.", -1)) {
        if (atom.isEmpty()) return false;
        for (int c : atom.codePoints().toArray())
          if (!(Character.isAlphabetic(c)
              || Character.getType(c) == Character.DECIMAL_DIGIT_NUMBER
              || Character.getType(c) == Character.LETTER_NUMBER
              || Character.getType(c) == Character.OTHER_NUMBER
              || "!#$%&'*+-/=?^_`{|}~".indexOf(c) >= 0
              || legacy(c))) return false;
      }
    }
    if (domain.startsWith("[") && domain.endsWith("]")) {
      String literal = domain.substring(1, domain.length() - 1);
      if (literal.startsWith("IPv6:")) {
        String ip = literal.substring(5);
        if (!ip.contains(":") || ip.contains("%")) return false;
        try {
          return InetAddress.getByName(ip).getAddress().length == 16;
        } catch (java.net.UnknownHostException e) {
          return false;
        }
      }
      String[] parts = literal.split("\\.", -1);
      if (parts.length != 4) return false;
      for (String part : parts) {
        if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) return false;
      }
      return true;
    }
    if (domain.isEmpty() || domain.length() > 253 || !domain.matches("[A-Za-z0-9.-]+"))
      return false;
    for (String label : domain.split("\\.", -1)) {
      if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-"))
        return false;
      if (label.length() >= 4 && label.substring(2, 4).equals("--") && !label.startsWith("xn--"))
        return false;
      if (label.startsWith("xn--")) {
        var info = new IDNA.Info();
        var decoded = new StringBuilder();
        IDNA.getUTS46Instance(
                IDNA.CHECK_BIDI
                    | IDNA.CHECK_CONTEXTJ
                    | IDNA.NONTRANSITIONAL_TO_UNICODE
                    | IDNA.NONTRANSITIONAL_TO_ASCII)
            .labelToUnicode(label, decoded, info);
        if (info.hasErrors()
            || decoded.toString().equals(label)
            || !unicodeLabel(decoded.toString())) return false;
      }
    }
    return true;
  }

  private static boolean legacy(int c) {
    return c >>> 8 >= 0xc2
        && c >>> 8 <= 0xdf
        && (c & 255) >= 0x80
        && (c & 255) <= 0xbf
        && !(c >= 0xd800 && c <= 0xdfff);
  }

  private static boolean unicodeLabel(String label) {
    int[] cs = label.codePoints().toArray();
    if (cs.length == 0 || mark(cs[0])) return false;
    boolean dot = false, japanese = false, arabic = false, extended = false;
    for (int i = 0; i < cs.length; i++) {
      int c = cs[i], prev = i == 0 ? 0 : cs[i - 1], next = i + 1 == cs.length ? 0 : cs[i + 1];
      if (c == 0xb7 && (prev != 'l' || next != 'l')) return false;
      if (c == 0x375 && !(next >= 0x370 && next <= 0x3ff)) return false;
      if ((c == 0x5f3 || c == 0x5f4) && !(prev >= 0x590 && prev <= 0x5ff)) return false;
      if (c == 0x30fb) {
        dot = true;
        continue;
      }
      if (c >= 0x3040 && c <= 0x309f || c >= 0x30a0 && c <= 0x30ff || c >= 0x4e00 && c <= 0x9fff) {
        japanese = true;
        continue;
      }
      if (c >= 0x660 && c <= 0x669) arabic = true;
      if (c >= 0x6f0 && c <= 0x6f9) extended = true;
      if (c == 0x640
          || c == 0x7fa
          || c == 0x302e
          || c == 0x302f
          || c >= 0x3031 && c <= 0x3035
          || c == 0x303b) return false;
      if (c == 0x200c
          || c == 0x200d
          || c == 0xb7
          || c == 0x375
          || c == 0x5f3
          || c == 0x5f4
          || c == 0x6fd
          || c == 0x6fe
          || c == 0xf0b
          || c == 0x3007) continue;
      if (c > 127
          && !Character.isLetter(c)
          && !mark(c)
          && Character.getType(c) != Character.DECIMAL_DIGIT_NUMBER) return false;
    }
    return !(dot && !japanese || arabic && extended);
  }

  private static boolean mark(int c) {
    int t = Character.getType(c);
    return t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK;
  }
}
