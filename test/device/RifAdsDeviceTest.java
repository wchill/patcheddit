/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("deprecation")
public final class RifAdsDeviceTest {
    private static final String ADS = "com.andrewshu.android.reddit.ads.";
    private static final String BLOCK = "morphe_patch_block_ads";
    private static final String PREMIUM = "morphe_patch_fake_reddit_premium";

    public static void run(Context context, Bundle arguments) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Class<?> settings = loader.loadClass("app.morphe.extension.shared.settings.PatchSettings");
        Class<?> helperType;
        try {
            helperType = loader.loadClass(ADS + "AdViewHelper");
        } catch (ClassNotFoundException paid) {
            for (Method method : settings.getDeclaredMethods()) {
                check(!method.getName().equals("isEnabled_" + BLOCK), "Block ads must not be available in Platinum");
            }
            return;
        }
        Method blockGetter = settings.getDeclaredMethod("isEnabled_" + BLOCK);
        Method premiumGetter = settings.getDeclaredMethod("isEnabled_" + PREMIUM);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Object previousBlock = preferences.getAll().get(BLOCK);
        Object previousPremium = preferences.getAll().get(PREMIUM);
        check(previousBlock == null || previousBlock instanceof Boolean, "Unexpected Block ads preference type");
        check(previousPremium == null || previousPremium instanceof Boolean, "Unexpected premium preference type");
        try {
            new Fixture(loader, helperType, preferences, blockGetter, premiumGetter).run();
        } finally {
            SharedPreferences.Editor restore = preferences.edit();
            restore(restore, BLOCK, previousBlock);
            restore(restore, PREMIUM, previousPremium);
            check(restore.commit(), "Could not restore original feature settings");
        }
    }

    private static final class Fixture {
        private final ClassLoader loader;
        private final SharedPreferences preferences;
        private final Method blockGetter;
        private final Method premiumGetter;
        private final Object unsafe;
        private final Method allocate;
        private final Object feed;
        private final Object album;
        private final Object manager;
        private final Object retry;
        private final Object banner;
        private final Object bannerView;
        private final Object slotOwner;
        private final Object layout;
        private final Object activity;
        private final Object imageAdapter;
        private final ArrayList<Object> images;
        private final List<Object> originalImages;
        private final Method slot;
        private final Method layoutGate;
        private final Method factory;
        private final Method adjustedPosition;
        private final Method count;
        private final Method nativeLoad;
        private final Method dequeue;
        private final Method retryLoad;
        private final Method bannerLoad;
        private final Method premium;
        private final Class<?> adapterType;

        @SuppressWarnings("unchecked")
        Fixture(ClassLoader loader, Class<?> helperType, SharedPreferences preferences,
                Method blockGetter, Method premiumGetter) throws Exception {
            this.loader = loader;
            this.preferences = preferences;
            this.blockGetter = blockGetter;
            this.premiumGetter = premiumGetter;
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field singleton;
            try {
                singleton = unsafeType.getDeclaredField("theUnsafe");
            } catch (NoSuchFieldException alternateAndroidName) {
                singleton = unsafeType.getDeclaredField("THE_ONE");
            }
            singleton.setAccessible(true);
            unsafe = singleton.get(null);
            allocate = unsafeType.getMethod("allocateInstance", Class.class);
            feed = fresh(helperType);
            album = fresh(type(ADS + "ImageAlbumAdViewHelper"));
            manager = fresh(type(ADS + "RifNativeAdLoaderWaitListManager"));
            retry = fresh(type(ADS + "RifNativeAdLoaderWaitListManager$RetryLoadRunnable"));
            banner = fresh(type(ADS + "BannerAdViewHelper"));
            bannerView = fresh(type("com.applovin.mediation.ads.MaxAdView"));
            slotOwner = fresh(type("e5.e0"));
            layout = fresh(type("u1.b"));
            activity = fresh(Activity.class);
            // A non-null SDK shell skips SDK construction; the null Handler fails before any loadAd().
            set(manager, "mMaxNativeAdLoader", fresh(type("com.applovin.mediation.nativeAds.MaxNativeAdLoader")));
            adapterType = type("androidx.recyclerview.widget.RecyclerView$h");
            imageAdapter = type("g2.f").getConstructor(type("v1.k")).newInstance(new Object[] { null });
            images = (ArrayList<Object>) field(type("e2.c"), "j").get(imageAdapter);
            originalImages = Arrays.asList(new Object(), new Object(), new Object());
            images.addAll(originalImages);
            count = method(type("e2.c"), "o");
            slot = method(slotOwner.getClass(), "d0");
            layoutGate = method(layout.getClass(), "b", type("com.andrewshu.android.reddit.MainActivity"));
            factory = method(album.getClass(), "newRecyclerAdapter", Activity.class, adapterType);
            adjustedPosition = method(album.getClass(), "getAdjustedPosition", adapterType, int.class);
            nativeLoad = method(manager.getClass(), "loadAds", Activity.class);
            dequeue = method(manager.getClass(), "dequeueAd");
            retryLoad = method(retry.getClass(), "run");
            bannerLoad = method(banner.getClass(), "loadAd", View.class, boolean.class);
            premium = method(type("o5.i0"), "a");
            for (Method target : Arrays.asList(slot, layoutGate, factory, nativeLoad, dequeue, retryLoad, bannerLoad)) {
                nativeCopy(target);
            }
        }

        void run() throws Exception {
            for (boolean blocking : new boolean[] { true, false, true }) {
                for (boolean fakePremium : new boolean[] { false, true }) {
                    check(preferences.edit().putBoolean(BLOCK, blocking).putBoolean(PREMIUM, fakePremium).commit(),
                            "Could not set feature switches");
                    check(Boolean.valueOf(blocking).equals(blockGetter.invoke(null)), "Block ads switch is not live");
                    check(Boolean.valueOf(fakePremium).equals(premiumGetter.invoke(null)), "Premium switch was changed");
                    Object premiumBefore = premium.invoke(null);
                    if (fakePremium) check(Boolean.TRUE.equals(premiumBefore), "Premium pro predicate was lost");
                    checkHelper(feed, "mThreadsWaitListManager", blocking);
                    checkHelper(album, "mImageAlbumAdWaitListManager", blocking);
                    if (blocking) {
                        check(Boolean.FALSE.equals(slot.invoke(slotOwner)), "Native ad rows remain enabled");
                        check(Boolean.FALSE.equals(layoutGate.invoke(layout, new Object[] { null })),
                                "Banner placement can reserve an empty ad container");
                        nativeLoad.invoke(manager, new Object[] { null });
                        check(dequeue.invoke(manager) == null, "Cached native ads can still be dequeued");
                        retryLoad.invoke(retry);
                        bannerLoad.invoke(banner, bannerView, false);
                        bannerLoad.invoke(banner, bannerView, true);
                        for (int recycle = 0; recycle < 3; recycle++) {
                            check(factory.invoke(album, null, imageAdapter) == imageAdapter,
                                    "Album constructed an ad-inserting wrapper");
                            for (int position = 0; position < images.size(); position++) {
                                check(Integer.valueOf(position).equals(adjustedPosition.invoke(album, imageAdapter, position)),
                                        "Image paging position was changed");
                            }
                            for (String lifecycle : new String[] { "onFragmentShown", "onFragmentHidden", "destroy" }) {
                                method(album.getClass(), lifecycle, adapterType).invoke(album, imageAdapter);
                            }
                        }
                    } else {
                        // All sentinels fail on null native state before reaching an SDK load call.
                        expectNativeNull(slot, slotOwner);
                        expectNativeNull(layoutGate, layout, new Object[] { null });
                        expectNativeNull(nativeLoad, manager, new Object[] { null });
                        expectNativeNull(dequeue, manager);
                        expectNativeNull(retryLoad, retry);
                        expectNativeNull(bannerLoad, banner, bannerView, false);
                        Object wrapped = factory.invoke(album, null, imageAdapter);
                        check(type(ADS + "RifAppLovinImageAlbumRecyclerAdapter").isInstance(wrapped),
                                "Disabling blocking did not restore the native album factory");
                        method(wrapped.getClass(), "destroy").invoke(wrapped);
                    }
                    check(Integer.valueOf(3).equals(count.invoke(imageAdapter)), "Image count was changed");
                    check(images.equals(originalImages), "Image order was changed");
                    check(premiumBefore.equals(premium.invoke(null)), "Ad blocking changed the premium predicate");
                    check(Boolean.valueOf(fakePremium).equals(premiumGetter.invoke(null)), "Ad blocking changed premium settings");
                }
            }
        }

        private void checkHelper(Object helper, String managerField, boolean blocking) throws Exception {
            Method gate = method(helper.getClass(), "isAdsEnabledAndUnblocked");
            Method load = method(helper.getClass(), "checkConditionsAndLoadInitialAds");
            Method nativeGate = nativeCopy(gate);
            Method originalLoad = nativeCopy(load);
            check(blocking ? Boolean.FALSE.equals(gate.invoke(helper)) :
                    gate.invoke(helper).equals(nativeGate.invoke(helper)), "Native ad eligibility did not follow its switch");
            set(helper, "mSdkInitializationComplete", true);
            set(helper, "mAdapterReadyToLoadAds", true);
            set(helper, "mInitialAdLoadAttempted", false);
            set(helper, managerField, manager);
            set(helper, "mActivity", blocking ? activity : null);
            load.invoke(helper);
            check(Boolean.FALSE.equals(field(helper.getClass(), "mInitialAdLoadAttempted").get(helper)),
                    "Blocked loader mutated its initial-load state");
            if (blocking) {
                // The native helper sets this flag; its nested manager remains independently blocked.
                originalLoad.invoke(helper);
                check(Boolean.TRUE.equals(field(helper.getClass(), "mInitialAdLoadAttempted").get(helper)),
                        "Native loader sentinel was not reached");
            }
        }

        private Class<?> type(String name) throws ClassNotFoundException {
            return loader.loadClass(name);
        }

        private Object fresh(Class<?> type) throws Exception {
            return allocate.invoke(unsafe, type);
        }
    }

    private static void set(Object receiver, String name, Object value) throws Exception {
        field(receiver.getClass(), name).set(receiver, value);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Method nativeCopy(Method original) {
        Method found = null;
        for (Method candidate : original.getDeclaringClass().getDeclaredMethods()) {
            if (candidate.getName().startsWith("morpheNative_" + original.getName() + "_") &&
                    Arrays.equals(candidate.getParameterTypes(), original.getParameterTypes()) &&
                    candidate.getReturnType() == original.getReturnType()) {
                check(found == null, "Ambiguous preserved native method: " + original);
                found = candidate;
            }
        }
        check(found != null, "Native implementation was not preserved: " + original);
        found.setAccessible(true);
        return found;
    }

    private static void expectNativeNull(Method method, Object receiver, Object... arguments) throws Exception {
        try {
            method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            check(failure.getCause() instanceof NullPointerException,
                    "Unexpected native sentinel failure in " + method + ": " + failure.getCause());
            return;
        }
        throw new AssertionError("Disabling blocking did not reach native logic: " + method);
    }

    private static void restore(SharedPreferences.Editor editor, String key, Object previous) {
        if (previous == null) editor.remove(key);
        else editor.putBoolean(key, (Boolean) previous);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
