package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "composite_weight_preference")
public class CompositeWeightPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal dcfWeight;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal grahamWeight;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal ddmWeight;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public BigDecimal getDcfWeight() { return dcfWeight; }
    public void setDcfWeight(BigDecimal dcfWeight) { this.dcfWeight = dcfWeight; }
    public BigDecimal getGrahamWeight() { return grahamWeight; }
    public void setGrahamWeight(BigDecimal grahamWeight) { this.grahamWeight = grahamWeight; }
    public BigDecimal getDdmWeight() { return ddmWeight; }
    public void setDdmWeight(BigDecimal ddmWeight) { this.ddmWeight = ddmWeight; }
}
