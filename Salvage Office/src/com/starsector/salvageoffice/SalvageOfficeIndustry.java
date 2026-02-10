package com.starsector.salvageoffice;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import java.util.Random;


import java.util.ArrayList;
import java.util.List;

public class SalvageOfficeIndustry extends BaseIndustry {

    private IntervalUtil scanInterval;

    // co ile dni skanować system
    private static final float SCAN_INTERVAL_DAYS = 2f;

    private static final Random random = new Random();


    @Override
    public void apply() {
        super.apply(true);

        demand(Commodities.SUPPLIES, 3);
        demand(Commodities.FUEL, 3);

        supply(Commodities.SUPPLIES, 3);
        supply(Commodities.FUEL, 3);

        if (scanInterval == null) {
            scanInterval = new IntervalUtil(SCAN_INTERVAL_DAYS, SCAN_INTERVAL_DAYS);
            scanInterval.forceIntervalElapsed();
        }
    }


    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (Global.getSector() == null) return;
        if (market == null) return;
        if (scanInterval == null) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        scanInterval.advance(days);

        if (scanInterval.intervalElapsed()) {

            Global.getSector().getCampaignUI().addMessage(
                    "Salvage Office tick | functional=" + isFunctional()
            );

            // NA CZAS TESTÓW – NIE BLOKUJEMY
            scanForDerelictsAndDebris();
        }
    }


    private void scanForDerelictsAndDebris() {
        if (market == null) return;
        if (market.getStarSystem() == null) return;
        if (!market.hasSubmarket(Submarkets.SUBMARKET_STORAGE)) return;

        List<SectorEntityToken> targets = new ArrayList<>();

        // Debris fields
        targets.addAll(market.getStarSystem().getEntitiesWithTag(Tags.DEBRIS_FIELD));

        // Derelict ships (brak stałej w 0.98)
        targets.addAll(market.getStarSystem().getEntitiesWithTag("derelict"));

        if (targets.isEmpty()) {
            Global.getSector().getCampaignUI().addMessage(
                    "Salvage Office (" + market.getName() + "): no salvage targets found"
            );
            return;
        }

        SectorEntityToken target = null;
        for (SectorEntityToken t : targets) {
            if (t == null) continue;
            if (t.getContainingLocation() == null) continue;
            if (t.getMemoryWithoutUpdate().getBoolean("$salvageOffice_taken")) continue;
            target = t;
            break;
        }

        if (target == null) return;

        // Zabezpieczenie przed farmieniem
        target.getMemoryWithoutUpdate().set("$salvageOffice_taken", true, 999999f);

        CargoAPI cargo = market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo();

        // === PODSTAWOWY LOOT (ZAWSZE) ===
        int supplies = 10 + (int) (Math.random() * 15);
        int fuel = 5 + (int) (Math.random() * 10);

        cargo.addSupplies(supplies);
        cargo.addFuel(fuel);

        // === SZANSA NA STATEK ===
        boolean shipRecovered = false;
        boolean isDerelict = target.hasTag("derelict");
        float shipChance = isDerelict ? 0.35f : 0.10f;

        if (Math.random() < shipChance) {
            addRecoveredShipToStorage(cargo);
            shipRecovered = true;
        }


        // === USUNIĘCIE WRKU Z MAPY ===
        if (target.getContainingLocation() != null) {
            target.getContainingLocation().removeEntity(target);
        }

        // === KOMUNIKAT DLA GRACZA ===
        String msg = "Salvage Office (" + market.getName() + "): recovered "
                + supplies + " supplies and "
                + fuel + " fuel from " + target.getName();

        if (shipRecovered) {
            msg += " including a ship hull";
        }

        Global.getSector().getCampaignUI().addMessage(msg);
    }


    private void addRecoveredShipToStorage(CargoAPI cargo) {
        if (cargo == null) return;

        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        picker.add("lasher_Standard", 10f);
        picker.add("wolf_Standard", 8f);
        picker.add("hammerhead_Balanced", 6f);
        picker.add("enforcer_Assault", 5f);
        picker.add("falcon_Attack", 2f);
        picker.add("eagle_Balanced", 1f);

        String variantId = picker.pick();
        if (variantId == null) return;

        cargo.addMothballedShip(FleetMemberType.SHIP, variantId, null);

        FleetMemberAPI member =
                cargo.getMothballedShips().getMembersListCopy()
                        .get(cargo.getMothballedShips().getMembersListCopy().size() - 1);

        int dmods = 1 + random.nextInt(4);
        DModManager.addDMods(member, true, dmods, random);

        Global.getSector().getCampaignUI().addMessage(
                "Salvage Office (" + market.getName() + "): recovered ship hull (" +
                        member.getHullSpec().getHullName() + ") with " + dmods + " D-mods"
        );
    }




    private int getSuppliesDemand() {
        return 3;
    }

    private int getFuelDemand() {
        return 3;
    }

    // TESTOWO: bez wymogów, żeby budynek zawsze działał
    @Override
    public boolean isAvailableToBuild() {
        return true;
    }

    @Override
    public String getUnavailableReason() {
        return null;
    }
}
