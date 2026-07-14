package com.jipelski.mergerrealm.testutil;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

/**
 * Boots a headless LibGDX application once per test JVM so production code
 * that calls Gdx.app.log/error (which is most of this codebase's manager
 * classes) doesn't NPE when exercised from a plain JUnit test. No GL context,
 * no assets, no files — just enough to make the Gdx.app static non-null.
 *
 * Call ensureInitialized() from a @BeforeAll in any test class whose code
 * under test touches Gdx.app.
 */
public final class GdxTestSupport {

    private static boolean initialized = false;

    private GdxTestSupport() {}

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        new HeadlessApplication(new ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
        initialized = true;
    }
}
