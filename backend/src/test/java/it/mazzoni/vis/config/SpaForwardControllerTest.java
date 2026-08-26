package it.mazzoni.vis.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpaForwardControllerTest {

    @Test
    void forwardsApplicationRoutesToReactEntryPoint() {
        assertThat(new SpaForwardController().forwardToFrontend()).isEqualTo("forward:/index.html");
    }
}

