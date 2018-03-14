/*
 * MIT License
 *
 * Copyright (c) 2018, Vladimir Schneider, vladimir.schneider@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.vladsch.git.filecase.fixer;

import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;

import java.awt.Color;
import java.util.Collection;

@SuppressWarnings({ "WeakerAccess", "SameParameterValue" })
public class Utils {

    public static String toRgbString(Color color) {
        return (color == null) ? "rgb(0,0,0)" : "rgb(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ")";
    }

    public static String withContext(String text, String context, int pos, String prefix, String suffix) {
        StringBuilder sb = new StringBuilder();
        sb.append(text).append('\n');
        sb.append(prefix).append(context).append(suffix).append('\n');
        for (int i = 1; i < prefix.length(); i++) sb.append(' ');
        sb.append('^').append('\n');
        return sb.toString();
    }

    public static Color errorColor() {
        TextAttributes attribute = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES);
        Color color = JBColor.RED;
        if (attribute != null) {
            if (attribute.getForegroundColor() != null) {
                color = attribute.getForegroundColor();
            } else if (attribute.getEffectColor() != null) {
                color = attribute.getEffectColor();
            } else if (attribute.getErrorStripeColor() != null) {
                color = attribute.getErrorStripeColor();
            }
        }
        return color;
    }

    public static Color warningColor() {
        TextAttributes attribute = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES);
        Color color = JBColor.ORANGE;
        if (attribute != null) {
            if (attribute.getForegroundColor() != null) {
                color = attribute.getForegroundColor();
            } else if (attribute.getEffectColor() != null) {
                color = attribute.getEffectColor();
            } else if (attribute.getErrorStripeColor() != null) {
                color = attribute.getErrorStripeColor();
            }
        }
        return color;
    }

    public static float min(float... values) {
        float value = values.length > 0 ? values[0] : 0;
        for (float v : values) {
            if (value < v) value = v;
        }
        return value;
    }

    public static float max(float... values) {
        float value = values.length > 0 ? values[0] : 0;
        for (float v : values) {
            if (value > v) value = v;
        }
        return value;
    }

    public static float rangeLimit(float value, float min, float max) {
        return max(min(value, max), min);
    }

    public static Color mixedColor(Color originalColor, Color overlayColor) {
        final float[] hsbColor = Color.RGBtoHSB(originalColor.getRed(), originalColor.getGreen(), originalColor.getBlue(), new float[3]);
        ;
        final float[] hsbError = Color.RGBtoHSB(overlayColor.getRed(), overlayColor.getGreen(), overlayColor.getBlue(), new float[3]);
        final float[] hsbMixed = new float[3];

        hsbMixed[0] = hsbError[0];
        hsbMixed[1] = min(max(rangeLimit(hsbColor[1], hsbError[1], 0.3f), 0.5f), 1.0f);
        hsbMixed[2] = min(max(rangeLimit(hsbColor[2], hsbError[2], 0.3f), 0.5f), 1.0f);
        Color errorColor = Color.getHSBColor(hsbMixed[0], hsbMixed[1], hsbMixed[2]);
        return errorColor;
    }

    public static Color errorColor(Color color) {
        return mixedColor(color, errorColor());
    }

    public static Color warningColor(Color color) {
        return mixedColor(color, warningColor());
    }

    public static String join(String[] items, String prefix, String suffix, String itemPrefix, String itemSuffix) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        for (String item : items) {
            sb.append(itemPrefix).append(item).append(itemSuffix);
        }
        sb.append(suffix);
        return sb.toString();
    }

    public static String join(Collection<String> items, String prefix, String suffix, String itemPrefix, String itemSuffix) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        for (String item : items) {
            sb.append(itemPrefix).append(item).append(itemSuffix);
        }
        sb.append(suffix);
        return sb.toString();
    }
}

