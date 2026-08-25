package lampas.levelledmobs.nametag;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance text formatting engine supporting colors, hex (#RRGGBB), legacy codes (& / §), and tag syntax (<color>).
 */
public class TextFormatter {
    public static final Pattern TAG_PATTERN = Pattern.compile("<(/?[#a-zA-Z0-9_]+)>");

    /**
     * Formats a raw text string with legacy or tag-based styling into a Minecraft Component.
     */
    public static Component format(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        // 1. Convert legacy codes (&a, &c, &6, etc.) to tag-like formatting
        String converted = translateLegacy(input);

        // 2. Parse tags into styled Component
        return parseTags(converted);
    }

    public static String translateLegacy(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length() + 16);
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < len) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                String tag = switch (code) {
                    case '0' -> "<black>";
                    case '1' -> "<dark_blue>";
                    case '2' -> "<dark_green>";
                    case '3' -> "<dark_aqua>";
                    case '4' -> "<dark_red>";
                    case '5' -> "<dark_purple>";
                    case '6' -> "<gold>";
                    case '7' -> "<gray>";
                    case '8' -> "<dark_gray>";
                    case '9' -> "<blue>";
                    case 'a' -> "<green>";
                    case 'b' -> "<aqua>";
                    case 'c' -> "<red>";
                    case 'd' -> "<light_purple>";
                    case 'e' -> "<yellow>";
                    case 'f' -> "<white>";
                    case 'r' -> "<reset>";
                    default -> null;
                };
                if (tag != null) {
                    sb.append(tag);
                    i++; // skip code character
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static Component parseTags(String text) {
        MutableComponent root = Component.empty();
        int lastIndex = 0;
        TextColor currentColor = null;

        Matcher matcher = TAG_PATTERN.matcher(text);

        while (matcher.find()) {
            int start = matcher.start();
            if (start > lastIndex) {
                String textSegment = text.substring(lastIndex, start);
                MutableComponent comp = Component.literal(textSegment);
                if (currentColor != null) {
                    comp.setStyle(Style.EMPTY.withColor(currentColor));
                }
                root.append(comp);
            }

            String tag = matcher.group(1).toLowerCase();
            if (tag.startsWith("/")) {
                currentColor = null; // Reset style on closing tag </...>
            } else {
                currentColor = resolveColor(tag);
            }
            lastIndex = matcher.end();
        }

        if (lastIndex < text.length()) {
            String trailing = text.substring(lastIndex);
            MutableComponent comp = Component.literal(trailing);
            if (currentColor != null) {
                comp.setStyle(Style.EMPTY.withColor(currentColor));
            }
            root.append(comp);
        }

        return root;
    }

    public static TextColor resolveColor(String tag) {
        if (tag == null || tag.isEmpty()) return null;
        if (tag.startsWith("#")) {
            return TextColor.parseColor(tag).result().orElse(null);
        }

        return switch (tag) {
            case "black" -> TextColor.fromRgb(0x000000);
            case "dark_blue" -> TextColor.fromRgb(0x0000AA);
            case "dark_green" -> TextColor.fromRgb(0x00AA00);
            case "dark_aqua" -> TextColor.fromRgb(0x00AAAA);
            case "dark_red" -> TextColor.fromRgb(0xAA0000);
            case "dark_purple" -> TextColor.fromRgb(0xAA00AA);
            case "gold" -> TextColor.fromRgb(0xFFAA00);
            case "gray" -> TextColor.fromRgb(0xAAAAAA);
            case "dark_gray" -> TextColor.fromRgb(0x555555);
            case "blue" -> TextColor.fromRgb(0x5555FF);
            case "green" -> TextColor.fromRgb(0x55FF55);
            case "aqua" -> TextColor.fromRgb(0x55FFFF);
            case "red" -> TextColor.fromRgb(0xFF5555);
            case "light_purple" -> TextColor.fromRgb(0xFF55FF);
            case "yellow" -> TextColor.fromRgb(0xFFFF55);
            case "white" -> TextColor.fromRgb(0xFFFFFF);
            default -> null;
        };
    }
}
