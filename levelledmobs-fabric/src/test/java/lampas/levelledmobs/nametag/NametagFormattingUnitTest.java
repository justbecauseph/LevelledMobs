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
    public void testTextFormatterColorTags() {
        String input = "<yellow>Level 5</yellow> <gray>Zombie</gray>";
        Component component = TextFormatter.format(input);

        assertNotNull(component);
        assertEquals("Level 5 Zombie", component.getString());
    }

    @Test
    public void testTextFormatterHexCodes() {
        String input = "<#FF5555>Dangerous</#FF5555> <#55FF55>Creeper</#55FF55>";
        Component component = TextFormatter.format(input);

        assertNotNull(component);
        assertEquals("Dangerous Creeper", component.getString());
    }

    @Test
    public void testTextFormatterLegacyFormatting() {
        String input = "&cHardcore &aSkeleton";
        Component component = TextFormatter.format(input);

        assertNotNull(component);
        assertEquals("Hardcore Skeleton", component.getString());
    }

    @Test
    public void testTextFormatterNullOrEmpty() {
        Component nullComponent = TextFormatter.format(null);
        assertNotNull(nullComponent);
        assertEquals("", nullComponent.getString());

        Component empty = TextFormatter.format("");
        assertNotNull(empty);
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
        assertEquals(NametagVisibility.HOVER_ONLY, service.getVisibility());
        assertFalse(service.isNametagVisible());

        service.setVisibility(NametagVisibility.ALWAYS);
        assertTrue(service.isNametagVisible());

        service.setNametagVisible(false);
        assertFalse(service.isNametagVisible());

        service.setTemplate("<red>Boss Lv. <level></red>");
        assertEquals("<red>Boss Lv. <level></red>", service.getTemplate().template());
    }
}
