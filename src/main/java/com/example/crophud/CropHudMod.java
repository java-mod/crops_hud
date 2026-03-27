package com.example.crophud;

import com.example.crophud.hud.HudPositionStore;
import com.example.crophud.session.CropHarvestTracker;
import com.example.crophud.session.CropPriceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CropHudMod {
    public static final String MOD_ID = "crophud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CropPriceStore priceStore;
    private static HudPositionStore hudPositionStore;
    private static CropHarvestTracker tracker;

    private CropHudMod() {
    }

    public static void initializeClient() {
        if (priceStore == null) {
            priceStore = new CropPriceStore();
            priceStore.load();
        }
        if (hudPositionStore == null) {
            hudPositionStore = new HudPositionStore();
            hudPositionStore.load();
        }
        if (tracker == null) {
            tracker = new CropHarvestTracker(priceStore, hudPositionStore);
            tracker.initialize();
        }
    }

    public static CropPriceStore priceStore() {
        return priceStore;
    }

    public static CropHarvestTracker tracker() {
        return tracker;
    }

    public static HudPositionStore hudPositionStore() {
        return hudPositionStore;
    }
}
