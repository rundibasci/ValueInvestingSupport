package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.WaccResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WaccResultRepository extends JpaRepository<WaccResultEntity, UUID> {}
