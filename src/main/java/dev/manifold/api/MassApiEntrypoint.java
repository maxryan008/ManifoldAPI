package dev.manifold.api;

import net.minecraft.world.item.Item;

/**
 * An entrypoint interface for external mods to register default item masses with the Manifold Mass system.
 * <p>
 * Mods that wish to provide default mass values for their items should implement this interface and
 * declare an entrypoint under the {@code "manifold:mass"} key in their {@code fabric.mod.json}.
 * <p>
 * This allows optional integration: if the Manifold mod is present, these default masses will be
 * registered before world loading or mass calculations.
 */
public interface MassApiEntrypoint {

    /**
     * Called once during server startup before mass data is loaded or calculated.
     * Implementations should use the provided {@link MassRegistry} to register
     * default mass values for their custom items.
     *
     * @param registry a {@link MassRegistry} instance for registering default item masses
     */
    void registerMasses(MassRegistry registry);

    /**
     * An API interface used by external mods to register default masses.
     * <p>
     * Registrations via this interface will only be applied if no manual or automatic
     * mass has already been defined for the item.
     */
    interface MassRegistry {

        /**
         * Registers a default mass for the given item.
         * This value will only be used if the item does not already have a saved or auto-generated mass.
         *
         * @param item the {@link Item} to associate with a mass value
         * @param mass the default mass value to use for this item
         */
        void register(Item item, double mass);
    }
}