package com.socialpilot.app.data.ai.image;

import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;

/** One image generation call. {@link Attempt#run} blocks on a worker; cancel may come from any thread. */
public interface ImageProvider {
    Attempt prepare(AiRequest request, String size);

    interface Attempt {
        Response run();

        void cancel();
    }

    final class Response {
        public enum Outcome { SUCCEEDED, FAILED, CANCELLED }

        public final Outcome outcome;
        /** Decoded image bytes when SUCCEEDED; never a URL. */
        public final byte[] image;
        public final AiError error;
        /** False only when the request certainly never left the device. */
        public final boolean mayHaveDispatched;
        public final AiUsage usage;

        private Response(Outcome outcome, byte[] image, AiError error, boolean mayHaveDispatched, AiUsage usage) {
            this.outcome = outcome;
            this.image = image;
            this.error = error;
            this.mayHaveDispatched = mayHaveDispatched;
            this.usage = usage == null ? AiUsage.MISSING : usage;
        }

        public static Response succeeded(byte[] image, AiUsage usage) {
            return new Response(Outcome.SUCCEEDED, image, null, true, usage);
        }

        public static Response failed(AiError error, boolean mayHaveDispatched) {
            return new Response(Outcome.FAILED, null, error, mayHaveDispatched, null);
        }

        public static Response cancelled(boolean mayHaveDispatched) {
            return new Response(Outcome.CANCELLED, null, null, mayHaveDispatched, null);
        }
    }
}
