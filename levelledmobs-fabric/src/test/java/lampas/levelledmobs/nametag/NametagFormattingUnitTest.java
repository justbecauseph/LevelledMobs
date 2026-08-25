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

    @Test
    public void testNametagTokenizationWithPlaceholdersAndColors() {
        NametagTemplate template = new NametagTemplate("&7Lv. &e<level> &f<mob_name> &a[<health>/<max_health>]");
        // Test rendering with null entity (graceful fallback)
        Component nullRender = template.render(null, 15, "default");
        assertEquals("", nullRender.getString());
    }

    @Test
    public void testTranslateLegacySinglePassEquivalence() {
        String legacy = "&00&11&22&33&44&55&66&77&88&99&aa&bb&cc&dd&ee&ff&rr §aSection";
        String translated = TextFormatter.translateLegacy(legacy);
        assertTrue(translated.contains("<black>0"));
        assertTrue(translated.contains("<green>a"));
        assertTrue(translated.contains("<green>Section"));
    }

    private static class DummyZombie extends net.minecraft.world.entity.LivingEntity {
        private float health = 18.0f;

        protected DummyZombie() {
            super(null, null);
        }

        public static DummyZombie create() {
            try {
                java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
                DummyZombie entity = (DummyZombie) unsafe.allocateInstance(DummyZombie.class);
                entity.health = 18.0f;

                java.lang.reflect.Field attrField = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attributes");
                attrField.setAccessible(true);
                attrField.set(entity, new net.minecraft.world.entity.ai.attributes.AttributeMap(createLivingAttributes().build()));

                java.lang.reflect.Field typeField = net.minecraft.world.entity.Entity.class.getDeclaredField("type");
                typeField.setAccessible(true);
                typeField.set(entity, net.minecraft.world.entity.EntityTypes.ZOMBIE);

                return entity;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public float getHealth() {
            return health;
        }

        @Override
        public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {}

        @Override
        public net.minecraft.world.entity.HumanoidArm getMainArm() {
            return net.minecraft.world.entity.HumanoidArm.RIGHT;
        }
    }

    @Test
    public void testNametagRenderingWithEntityPlaceholdersAndStyles() {
        DummyZombie dummy = DummyZombie.create();
        NametagTemplate template = new NametagTemplate("&7Lv. &e<level> &f<mob_name> &a[<health>/<max_health>] <rule>");
        Component rendered = template.render(dummy, 15, "test_rule");

        assertNotNull(rendered);
        String text = rendered.getString();
        assertTrue(text.contains("Lv. 15"), "Rendered text should contain 'Lv. 15', got: " + text);
        assertTrue(text.contains("18/20"), "Rendered text should contain integer health '18/20', got: " + text);
        assertTrue(text.contains("test_rule"), "Rendered text should contain rule name 'test_rule', got: " + text);
    }
}
