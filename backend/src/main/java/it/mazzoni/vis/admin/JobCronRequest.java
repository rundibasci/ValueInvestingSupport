package it.mazzoni.vis.admin;

import jakarta.validation.constraints.NotBlank;

public record JobCronRequest(@NotBlank String cron) {
}
