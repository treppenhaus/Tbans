package eu.treppi.tbans.api;

/**
 * Static access to the {@link TbansApi} instance.
 *
 * <pre>{@code
 * TbansApi tbans = TbansProvider.get();
 * tbans.getBanByCode("K7QX2M9P").ifPresent(ban -> ...);
 * }</pre>
 *
 * <p>Your plugin must declare a dependency on the plugin id {@code tbans} (optional or required) so
 * Velocity loads TBans first and its classes are visible to your plugin.
 */
public final class TbansProvider {

    private static volatile TbansApi instance;

    private TbansProvider() {
    }

    /**
     * @return the API instance
     * @throws IllegalStateException if TBans is not loaded (yet)
     */
    public static TbansApi get() {
        TbansApi api = instance;
        if (api == null) {
            throw new IllegalStateException("TBans API is not loaded. Is the TBans plugin installed, "
                    + "and does your plugin declare a dependency on 'tbans'?");
        }
        return api;
    }

    /** @return {@code true} if the API is available */
    public static boolean isAvailable() {
        return instance != null;
    }

    /** Internal: called by TBans on startup and shutdown. Not for use by other plugins. */
    public static void register(TbansApi api) {
        instance = api;
    }
}
