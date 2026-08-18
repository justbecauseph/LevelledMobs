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
        if (text == null) return "";
        return text
            .replace("&0", "<black>").replace("§0", "<black>")
            .replace("&1", "<dark_blue>").replace("§1", "<dark_blue>")
            .replace("&2", "<dark_green>").replace("§2", "<dark_green>")
            .replace("&3", "<dark_aqua>").replace("§3", "<dark_aqua>")
            .replace("&4", "<dark_red>").replace("§4", "<dark_red>")
            .replace("&5", "<dark_purple>").replace("§5", "<dark_purple>")
            .replace("&6", "<gold>").replace("§6", "<gold>")
            .replace("&7", "<gray>").replace("§7", "<gray>")
            .replace("&8", "<dark_gray>").replace("§8", "<dark_gray>")
            .replace("&9", "<blue>").replace("§9", "<blue>")
            .replace("&a", "<green>").replace("§a", "<green>")
            .replace("&b", "<aqua>").replace("§b", "<aqua>")
            .replace("&c", "<red>").replace("§c", "<red>")
            .replace("&d", "<light_purple>").replace("§d", "<light_purple>")
            .replace("&e", "<yellow>").replace("§e", "<yellow>")
            .replace("&f", "<white>").replace("§f", "<white>")
            .replace("&r", "<reset>").replace("§r", "<reset>");
    }

    public static Component parseTags(String text) {
        MutableComponent root = Component.empty();
        int lastIndex = 0;
        TextColor currentColor = null;

        Matcher matcher = Pattern.compile("<(/?[#a-zA-Z0-9_]+)>").matcher(text);

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

    private static TextColor resolveColor(String tag) {
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
