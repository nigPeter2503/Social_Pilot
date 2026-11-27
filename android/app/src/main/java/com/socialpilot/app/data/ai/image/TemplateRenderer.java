package com.socialpilot.app.data.ai.image;

import com.socialpilot.app.domain.agent.ImageSpec;

/** Renders the local template to PNG bytes at the spec's size. No network, no cost. */
public interface TemplateRenderer {
    byte[] render(ImageSpec spec, String headline, String footer);
}
