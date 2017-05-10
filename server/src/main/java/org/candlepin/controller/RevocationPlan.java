/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.controller;

import com.google.inject.persist.Transactional;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.policy.EntitlementRefusedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by wpoteat on 5/11/17.
 */
public class RevocationPlan {
    private Pool pool;
//    private long newQuantity;
    private List<Entitlement> entitlementsToRevoke;
    private Map<Entitlement, Long> entitlementsToAdjust;
    private Map<Pool, Long> sharedPools;
    private PoolCurator poolCurator;
    private Configuration config;
    private long newConsumed;

    public RevocationPlan(PoolCurator poolCurator, Configuration config) {
        entitlementsToRevoke = new ArrayList<Entitlement>();
        entitlementsToAdjust = new HashMap<Entitlement, Long>();
        sharedPools = new HashMap<Pool, Long>();
        this.poolCurator = poolCurator;
        this.config = config;
    }

    public void setPool(Pool pool) {
        this.pool = pool;
    }

    public Pool getPool() {
        return pool;
    }

    public void addEntitlmentToRevoke(Entitlement entitlement) {
        entitlementsToRevoke.add(entitlement);
    }

    public List<Entitlement> getEntitlementsToRevoke() {
        return entitlementsToRevoke;
    }

    public void addEntitlementToAdjust(Entitlement entitlement, long quantity) {
        entitlementsToAdjust.put(entitlement, quantity);
    }

    public void addEntitlementToRevoke(Entitlement entitlement) {
        entitlementsToRevoke.add(entitlement);
    }

    public Map<Entitlement, Long> getEntitlementsToAdjust() {
        return entitlementsToAdjust;
    }

    public void setPoolCurator(PoolCurator poolCurator) {
        this.setPoolCurator(poolCurator);
    }

    @Transactional
    public void execute(PoolManager poolManager) {
        pool = poolCurator.lockAndLoad(pool);
        populateSharedPools();
        // first determine shared pool counts where allotted units are not in use
        boolean needsToRevoke = reduceSharedPools();
        // if that reduction will not be enough, we then start revoking
        if (needsToRevoke) {
            determineExcessEntitlements();
            poolManager.revokeEntitlements(entitlementsToRevoke);
        }
        // here is where we actually change the source entitlement quantities for the shared pools.
        // We have to wait until we get here so that share pool entitlements we want revoked are gone
        for (Entitlement entitlement : entitlementsToAdjust.keySet()) {
            try {
                poolManager.adjustEntitlementQuantity(entitlement.getConsumer(),
                    entitlement, entitlementsToAdjust.get(entitlement).intValue());
            }
            catch (EntitlementRefusedException e) {
                // TODO: Could be multiple errors, but we'll just report the first one for now:
                throw new ForbiddenException(e.getResults().values().iterator().next().getErrors()
                    .get(0).toString());
            }
        }
    }

    private void populateSharedPools() {
        for (Pool sPool: poolCurator.listSharedPoolsFromMasterPool(pool)) {
            sharedPools.put(sPool, sPool.getQuantity());
        }
    }

    private boolean reduceSharedPools() {
        newConsumed = pool.getConsumed();
        long over = pool.getConsumed() + pool.getExported() - pool.getQuantity();
        boolean hasReduction = over > 0;
        for (Pool sPool : sharedPools.keySet()) {
            if (over > 0) {
                long excessCount = sPool.getQuantity() - sPool.getConsumed();
                long newQuantity = 0;
                if (excessCount > 0) {
                    Entitlement shareEnt = sPool.getSourceEntitlement();
                    if (over >= excessCount) {
                        newQuantity = sPool.getQuantity() - excessCount;
                        over = over - excessCount;
                        newConsumed -= excessCount;
                    }
                    else {
                        newQuantity = shareEnt.getQuantity() - over;
                        newConsumed -= over;
                        over = 0L;
                    }
                    addEntitlementToAdjust(shareEnt, newQuantity);
                    sharedPools.put(sPool, newQuantity);
                }
            }
        }
        return over > 0;
    }

    private void determineExcessEntitlements() {
        List<Pool> pools = new ArrayList<Pool>();
        pools.add(pool);
        pools.addAll(sharedPools.keySet());
        List<Entitlement> freeEntitlements = this.poolCurator.retrieveFreeEntitlementsOfPools(pools);

        long existing = pool.getQuantity();
        for (Entitlement ent : freeEntitlements) {
            if (newConsumed > existing) {
                if (!ent.getConsumer().isShare()) {
                    if (ent.getPool().getType().equals(Pool.PoolType.SHARE_DERIVED)) {
                        Entitlement source = ent.getPool().getSourceEntitlement();
                        long newQuantity = source.getQuantity();
                        if (entitlementsToAdjust.get(source) != null) {
                            newQuantity = entitlementsToAdjust.get(source) - ent.getQuantity();
                        }
                        addEntitlementToAdjust(source, newQuantity);
                    }
                    addEntitlementToRevoke(ent);
                    newConsumed -= ent.getQuantity();
                }
            }
        }
    }
}
