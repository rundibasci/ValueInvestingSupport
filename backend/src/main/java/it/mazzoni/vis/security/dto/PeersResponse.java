package it.mazzoni.vis.security.dto;

import java.util.List;

public record PeersResponse(
        String symbol,
        List<PeerItem> peers
) {}
