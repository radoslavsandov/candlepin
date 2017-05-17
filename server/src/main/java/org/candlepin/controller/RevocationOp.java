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
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.policy.EntitlementRefusedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Created by wpoteat on 5/11/17.
 */
public class RevocationOp {
    private List<Pool> pools;
    private Set<Entitlement> entitlementsToRevoke;
    private Map<Entitlement, Long> entitlementsToAdjust;
    private Map<Pool, List<Pool>> sharedPools;
    private PoolCurator poolCurator;
    private long newConsumed;

    public RevocationOp(PoolCurator poolCurator, List<Pool> pools) {
        entitlementsToRevoke = new HashSet<Entitlement>();
        entitlementsToAdjust = new HashMap<Entitlement, Long>();
        sharedPools = new HashMap<Pool, List<Pool>>();
        this.poolCurator = poolCurator;
        this.pools = pools;
    }

    public void addEntitlmentToRevoke(Entitlement entitlement) {
        entitlementsToRevoke.add(entitlement);
    }

    public Set<Entitlement> getEntitlementsToRevoke() {
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
        for (Pool pool : pools) {
            if (pool.isOverflowing()) {
                pool = poolCurator.lockAndLoad(pool);
                sharedPools.put(pool, poolCurator.listSharedPoolsOf(pool));
                // first determine shared pool counts where allotted units are not in use
                boolean needsToRevoke = reduceSharedPools(pool);
                // if that reduction will not be enough, we then start revoking
                if (needsToRevoke) {
                    determineExcessEntitlements(pool);
                }
            }
        }
        // revoke the entitlements amassed above
        poolManager.revokeEntitlements(new ArrayList<Entitlement>(entitlementsToRevoke));
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

    /**
     * The first part of the adjustment is to reduce the shared pool source entitlements for counts that are not
     *  in use as entitlements from the shared pool.
     *
     * @param pool
     * @return boolean
     */
    private boolean reduceSharedPools(Pool pool) {
        newConsumed = pool.getConsumed();
        long over = pool.getConsumed() - pool.getQuantity();
        for (Pool sPool : sharedPools.get(pool)) {
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
                }
            }
        }
        return over > 0;
    }

    /**
     * In the second part, the list of entitlements is compiled from the main pool plus the shared pools
     * It is sorted in the order of LIFO. Entitlements are put in the revoke list until the count is
     * acceptable for the main pool. Any entitlements that came from a shared pool are also reflected in
     * the adjustment for the source entitlement for that shared pool.
     *
     * @param pool
     */
    private void determineExcessEntitlements(Pool pool) {
        List<Pool> pools = new ArrayList<Pool>();
        pools.add(pool);
        pools.addAll(sharedPools.get(pool));
        List<Entitlement> freeEntitlements = this.poolCurator.retrieveOrderedEntitlementsOf(pools);

        long existing = pool.getQuantity();
        for (Entitlement ent : freeEntitlements) {
            if (newConsumed > existing) {
                if (!ent.getConsumer().isShare()) {
                    if (ent.getPool().getType().equals(Pool.PoolType.SHARE_DERIVED)) {
                        Entitlement source = ent.getPool().getSourceEntitlement();
                        if (entitlementsToAdjust.get(source) == null) {
                            addEntitlementToAdjust(source, source.getQuantity());
                        }
                        addEntitlementToAdjust(source, entitlementsToAdjust.get(source) - ent.getQuantity());
                    }
                    addEntitlementToRevoke(ent);
                    newConsumed -= ent.getQuantity();
                }
            }
        }
    }
}
