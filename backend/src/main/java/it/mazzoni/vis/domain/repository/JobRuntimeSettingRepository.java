package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.JobRuntimeSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRuntimeSettingRepository extends JpaRepository<JobRuntimeSetting, String> {
}
