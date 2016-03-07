package org.candlepin.api.dto;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.candlepin.model.Branding;
import org.candlepin.model.Cdn;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.SubscriptionsCertificate;

@XmlRootElement(name = "pool")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PoolDTO {

//    private String id;
//    private String productId;
//    private String productName;
//    private PoolType type;
//    private Boolean activeSubscription;
//    private Long quantity;
//    private Date startDate;
//    private Date endDate;
//    private Long consumed;
//    private Long exported;
//    private String restrictedToUsername;
//    private String contractNumber;
//    private String accountNumber;
//    private String orderNumber;
//    private String upstreamPoolId;
//    private String upstreamEntitlementId;
//    private String upstreamConsumerId;
//    private Owner owner;

    private Pool pool;


    public PoolDTO(Pool pool) {
        this.pool = pool;
    }


//    public PoolDTO(String id, String productName, String productId, PoolType type, Boolean activeSubscription, Long quantity, String startDate, String endDate,
//            Long consumed, Long exported, String restrictedToUsername, String contractNumber, String accountNumber,
//            String orderNumber, String upstreamPoolId, String upstreamEntitlementId,
//            String upstreamConsumerId) {
//        super();
//        this.id = id;
//        this.type = type;
//        this.activeSubscription = activeSubscription;
//        this.quantity = quantity;
//        this.startDate = startDate;
//        this.endDate = endDate;
//        this.consumed = consumed;
//        this.exported = exported;
//        this.restrictedToUsername = restrictedToUsername;
//        this.contractNumber = contractNumber;
//        this.accountNumber = accountNumber;
//        this.orderNumber = orderNumber;
//        this.upstreamPoolId = upstreamPoolId;
//        this.upstreamEntitlementId = upstreamEntitlementId;
//        this.upstreamConsumerId = upstreamConsumerId;
//        this.productName = productName;
//        this.productId = productId;
//    }


    public String getId() {
        return pool.getId();
    }
    public PoolType getType() {
        return pool.getType();
    }
    public Boolean isActiveSubscription() {
        return pool.getActiveSubscription();
    }
    public Long getQuantity() {
        return pool.getQuantity();
    }
    public Date getStartDate() {
        return pool.getStartDate();
    }
    public Date getEndDate() {
        return pool.getEndDate();
    }
    public Long getConsumed() {
        Long consumed = pool.getConsumed();
        return consumed == null ? 0 : consumed;
    }
    public Long getExported() {
        Long exported = pool.getExported();
        return exported == null ? 0 : exported;
    }
    public String getRestrictedToUsername() {
        return pool.getRestrictedToUsername();
    }
    public String getContractNumber() {
        return pool.getContractNumber();
    }
    public String getAccountNumber() {
        return pool.getAccountNumber();
    }
    public String getOrderNumber() {
        return pool.getOrderNumber();
    }
    public String getUpstreamPoolId() {
        return pool.getUpstreamPoolId();
    }
    public String getUpstreamEntitlementId() {
        return pool.getUpstreamEntitlementId();
    }
    public String getUpstreamConsumerId() {
        return pool.getUpstreamConsumerId();
    }
    public Owner getOwner() {
        return pool.getOwner();
    }

    public String getProductId() {
        return pool.getProductId();
    }


    public String getProductName() {
        return pool.getProductName();
    }



//    public int getVersion() {
//        return version;
//    }


//    private Entitlement sourceEntitlement;
//    private SourceStack sourceStack;
//    private SourceSubscription sourceSubscription;
//    private Product product; // SOME
//    private Product derivedProduct;
//    private Set<Product> providedProducts = new HashSet<Product>();
//    private Set<Product> derivedProvidedProducts = new HashSet<Product>();
//    private Set<ProvidedProduct> providedProductDtos = null;
//    private Set<ProvidedProduct> derivedProvidedProductDtos = null;
//    private Set<PoolAttribute> attributes = new HashSet<PoolAttribute>();
//    private Set<Entitlement> entitlements = new HashSet<Entitlement>();
//    private Set<Branding> branding = new HashSet<Branding>();
//    private Map<String, String> calculatedAttributes;
//    private SubscriptionsCertificate cert;
//    private Cdn cdn;



}
