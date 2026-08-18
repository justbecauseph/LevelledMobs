package lampas.levelledmobs.nametag;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NametagFormattingUnitTest {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testLegacyCodeTranslation() {
        String legacy = "&aLv. &e15 &cZombie &7[&a20&7/&a20&7]";
        String translated = TextFormatter.translateLegacy(legacy);

        assertTrue(translated.contains("<green>"));
        assertTrue(translated.contains("<yellow>"));
        assertTrue(translated.contains("<red>"));
        assertTrue(translated.contains("<gray>"));
    }

    @Test
    public void testTagParsingAndComponentGeneration() {
        Component component = TextFormatter.format("<yellow>Lv. 15</yellow> <white>Zombie</white>");
        assertNotNull(component);
        assertEquals("Lv. 15 Zombie", component.getString());

        // Hex color parsing
        Component hexComponent = TextFormatter.format("<#FF0000>Red Boss</#FF0000>");
        assertNotNull(hexComponent);
        assertEquals("Red Boss", hexComponent.getString());
    }

    @Test
    public void testMalformedSyntaxFailsGracefully() {
        // Unclosed tags or invalid characters must not throw exceptions
        assertDoesNotThrow(() -> TextFormatter.format("<invalid_tag_here>Test<<<>>"));
        assertDoesNotThrow(() -> TextFormatter.format(""));
        assertDoesNotThrow(() -> TextFormatter.format(null));

        Component empty = TextFormatter.format(null);
        assertEquals("", empty.getString());
    }

    @Test
    public void testNametagTemplatePlaceholderReplacements() {
        NametagTemplate template = new NametagTemplate("<gray>Lv. <yellow><level></yellow> <white><mob_name></white> <green>[<health>/<max_health>]</green>");

        assertEquals("<gray>Lv. <yellow><level></yellow> <white><mob_name></white> <green>[<health>/<max_health>]</green>", template.template());
    }

    @Test
    public void testNametagServiceConfiguration() {
        NametagService service = new NametagService("<gray>Lv. <yellow><level></yellow>");
        assertTrue(service.isNametagVisible());

        service.setNametagVisible(false);
        assertFalse(service.isNametagVisible());

        service.setTemplate("<red>Boss Lv. <level></red>");
        assertEquals("<red>Boss Lv. <level></red>", service.getTemplate().template());
    }
}
