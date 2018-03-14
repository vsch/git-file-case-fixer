package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

public class PluginIcons {
    static Icon load(String path) {
        return IconLoader.getIcon(path, PluginIcons.class);
    }

    public static final Icon FixerIcon = load("/icons/Fixer_icon.png");
    public static final Icon LeftArrow = load("/icons/Left_arrow.png");
    public static final Icon RightArrow = load("/icons/Right_arrow.png");
    public static final Icon NoArrow = load("/icons/No_arrow.png");
}
