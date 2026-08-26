package it.mazzoni.vis.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/", "/login", "/account", "/audit", "/checklists", "/screener",
            "/portfolio", "/watchlist", "/seed", "/universe-curation",
            "/admin/seed", "/admin/jobs", "/admin/fallbacks", "/admin/users",
            "/auth/oauth2/callback", "/securities/{symbol}", "/securities/{symbol}/review"
    })
    public String forwardToFrontend() {
        return "forward:/index.html";
    }
}

