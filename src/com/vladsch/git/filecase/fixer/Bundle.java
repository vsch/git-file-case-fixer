package com.vladsch.git.filecase.fixer;

import com.intellij.BundleBase;
import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

import static com.intellij.reference.SoftReference.dereference;

public class Bundle extends BundleBase {

    public static String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, @NotNull Object... params) {
        return CommonBundle.message(getBundle(), key, params);
    }

    private static Reference<ResourceBundle> ourBundle;
    @NonNls protected static final String PATH_TO_BUNDLE = "com.vladsch.git.filecase.fixer.localization.strings";

    private Bundle() {
    }

    public static String getString(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) final String key) {
        return getBundle().getString(key);
    }

    private static ResourceBundle getBundle() {
        ResourceBundle bundle = dereference(ourBundle);
        if (bundle == null) {
            bundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);
            ourBundle = new SoftReference<>(bundle);
        }
        return bundle;
    }
}
