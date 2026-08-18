package lampas.levelledmobs.nametag;

/**
 * Nametag visibility display modes.
 */
public enum NametagVisibility {
    /**
     * Nametag is always visible and rendered through blocks.
     */
    ALWAYS,

    /**
     * Nametag is only visible when aiming at the mob in line-of-sight (hidden behind walls and blocks).
     */
    HOVER_ONLY,

    /**
     * Nametag is completely disabled.
     */
    NEVER
}
