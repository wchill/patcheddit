package app.morphe.extension.boostforreddit.utils;

import java.lang.reflect.Method;
import java.util.List;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.Utils;

/**
 * @noinspection unused
 */
public class SettingsUtils {
    private static final String MENU_OPTION_CLASS = "com.rubenmayayo.reddit.ui.customviews.menu.MenuOption";
    private static final Method getSettingsUtilsInstance;
    private static final Method setMenuOptionDivider;
    private static final Method setMenuOptionValue;
    private static final Method setMenuOptionTitle;
    private static final Method setMenuOptionDrawable;

    static {
        getSettingsUtilsInstance = ReflectionUtils.findMethodWithClasses("id.b", "v0");
        setMenuOptionDivider = ReflectionUtils.findMethodWithClasses(MENU_OPTION_CLASS, "X", Boolean.TYPE);
        setMenuOptionValue = ReflectionUtils.findMethodWithClasses(MENU_OPTION_CLASS, "d0", Integer.TYPE);
        setMenuOptionTitle = ReflectionUtils.findMethodWithClasses(MENU_OPTION_CLASS, "h0", String.class);
        setMenuOptionDrawable = ReflectionUtils.findMethodWithClasses(MENU_OPTION_CLASS, "a0", Integer.TYPE);
    }

    public static void addMenuOptions(List options) {
        Object menuOption = ReflectionUtils.construct(MENU_OPTION_CLASS);
        ReflectionUtils.invoke(menuOption, setMenuOptionDivider, true);
        options.add(menuOption);

        int drawableId = Utils.getResourceIdentifier(ResourceType.DRAWABLE, "ic_library_24dp");
        menuOption = ReflectionUtils.construct(MENU_OPTION_CLASS);
        ReflectionUtils.invoke(menuOption, setMenuOptionValue, 101);
        ReflectionUtils.invoke(menuOption, setMenuOptionTitle, "Open with Wayback Machine");
        ReflectionUtils.invoke(menuOption, setMenuOptionDrawable, drawableId);
        options.add(menuOption);

        menuOption = ReflectionUtils.construct(MENU_OPTION_CLASS);
        ReflectionUtils.invoke(menuOption, setMenuOptionValue, 102);
        ReflectionUtils.invoke(menuOption, setMenuOptionTitle, "Open with Archive.is");
        ReflectionUtils.invoke(menuOption, setMenuOptionDrawable, drawableId);
        options.add(menuOption);
    }
}
