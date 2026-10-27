package com.noloxtreme.tts.reader;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Checks the optimized app without relying on shared AndroidX test libraries,
 * which may themselves have been removed or renamed in the target APK by R8.
 */
public final class TranslationReleaseStartupInstrumentation extends Instrumentation {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle status = new Bundle();
        status.putString("id", "InstrumentationTestRunner");
        status.putInt("numtests", 1);
        status.putInt("current", 1);
        status.putString("class", getClass().getName());
        status.putString("test", "translationRegistrarsCanBeConstructedFromInstalledApp");
        sendStatus(1, status);

        Bundle result = new Bundle();
        try {
            // String lookup through the target's loader leaves the actual SDK
            // constructors invisible to R8's analysis of the test's references.
            ClassLoader loader = getTargetContext().getClassLoader();
            for (String name : new String[] {
                "com.google.mlkit.common.internal.CommonComponentRegistrar",
                "com.google.mlkit.nl.translate.NaturalLanguageTranslateRegistrar"
            }) {
                Class.forName(name, true, loader).getDeclaredConstructor().newInstance();
            }
            sendStatus(0, status);
            result.putString("stream", "\nOK (1 test)\n");
        } catch (Throwable failure) {
            StringWriter stack = new StringWriter();
            failure.printStackTrace(new PrintWriter(stack));
            status.putString("stack", stack.toString());
            sendStatus(-2, status);
            result.putString("stream", "\nFAILURES!!!\nTests run: 1, Failures: 1\n" + stack);
        }
        finish(Activity.RESULT_OK, result);
    }
}
