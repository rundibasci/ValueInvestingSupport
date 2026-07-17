package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "roic_observation", uniqueConstraints =
        @UniqueConstraint(name = "uk_roic_observation_security_year", columnNames = {"security_id", "fiscal_year"}))
public class RoicObservation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Security security;

    @Column(nullable = false)
    private Integer fiscalYear;
    private LocalDate observationDate;

    @Column(precision = 16, scale = 8)
    private BigDecimal roic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoicSource source;

    @Column(length = 40)
    private String inputProvider;

    @Column(nullable = false, length = 500)
    private String formulaNote;

    @Column(length = 80)
    private String unavailableReason;

    public UUID getId() { return id; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Integer getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }
    public LocalDate getObservationDate() { return observationDate; }
    public void setObservationDate(LocalDate observationDate) { this.observationDate = observationDate; }
    public BigDecimal getRoic() { return roic; }
    public void setRoic(BigDecimal roic) { this.roic = roic; }
    public RoicSource getSource() { return source; }
    public void setSource(RoicSource source) { this.source = source; }
    public String getInputProvider() { return inputProvider; }
    public void setInputProvider(String inputProvider) { this.inputProvider = inputProvider; }
    public String getFormulaNote() { return formulaNote; }
    public void setFormulaNote(String formulaNote) { this.formulaNote = formulaNote; }
    public String getUnavailableReason() { return unavailableReason; }
    public void setUnavailableReason(String unavailableReason) { this.unavailableReason = unavailableReason; }
}
