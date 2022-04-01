package co.casterlabs.caffeinated.app.auth;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.caffeinated.app.CaffeinatedApp;
import co.casterlabs.caffeinated.util.async.AsyncTask;
import co.casterlabs.koi.api.KoiChatterType;
import co.casterlabs.koi.api.KoiConnection;
import co.casterlabs.koi.api.KoiIntegrationFeatures;
import co.casterlabs.koi.api.listener.KoiEventHandler;
import co.casterlabs.koi.api.listener.KoiEventUtil;
import co.casterlabs.koi.api.listener.KoiLifeCycleHandler;
import co.casterlabs.koi.api.stream.KoiStreamConfiguration;
import co.casterlabs.koi.api.stream.KoiStreamConfigurationFeatures;
import co.casterlabs.koi.api.types.events.KoiEvent;
import co.casterlabs.koi.api.types.events.RoomstateEvent;
import co.casterlabs.koi.api.types.events.StreamStatusEvent;
import co.casterlabs.koi.api.types.events.UserUpdateEvent;
import co.casterlabs.koi.api.types.events.ViewerListEvent;
import co.casterlabs.koi.api.types.user.User;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class AuthInstance implements KoiLifeCycleHandler, Closeable {
    private @Getter String tokenId;
    private @Getter String token;

    private FastLogger logger;
    private KoiConnection koi;

    private boolean disposed = false;

    private @Getter @Nullable User userData;
    private @Getter @Nullable StreamStatusEvent streamData;
    private @Getter @Nullable List<User> viewers;
    private @Getter @Nullable RoomstateEvent roomstate;

    private @Getter @Nullable List<KoiIntegrationFeatures> features = new ArrayList<>();
    private @Getter @Nullable Map<String, String> streamCategories;
    private @Getter @Nullable List<KoiStreamConfigurationFeatures> streamConfigurationFeatures;

    public AuthInstance(String tokenId) {
        this.tokenId = tokenId;

        this.logger = new FastLogger(String.format("AuthInstance (%d) ?", this.tokenId.hashCode()));

        this.token = CaffeinatedApp
            .getInstance()
            .getAuthPreferences()
            .get()
            .getKoiTokens()
            .getString(this.tokenId);

        FastLogger koiLogger = new FastLogger("AuthInstance Koi");
        koiLogger.setCurrentLevel(LogLevel.INFO);
        this.koi = new KoiConnection(
            KoiConnection.KOI_URL,
            koiLogger,
            this,
            CaffeinatedApp.caffeinatedClientId
        );

        this.reconnect();
    }

    public void invalidate() {
        try {
            this.close();
        } catch (IOException ignored) {}

        this.logger.info("I have been invalidate()'d, goodbye.");
        CaffeinatedApp.getInstance().getAuthPreferences().get().getKoiTokens().remove(this.tokenId);
        CaffeinatedApp.getInstance().getAuthPreferences().save();
        CaffeinatedApp.getInstance().getAuth().getAuthInstances().remove(this.tokenId);
        CaffeinatedApp.getInstance().getAuth().checkAuth();
        CaffeinatedApp.getInstance().getAuth().updateBridgeData();
    }

    public void sendChat(@NonNull String message, @NonNull KoiChatterType chatter) {
        if (this.isConnected()) {
            this.koi.sendChat(message, chatter);
        }
    }

    public void upvoteChat(@NonNull String messageId) {
        if (this.isConnected()) {
            this.koi.upvoteChat(messageId);
        }
    }

    public void deleteChat(@NonNull String messageId) {
        if (this.isConnected()) {
            this.koi.deleteChat(messageId);
        }
    }

    public void sendTest(@NonNull String eventType) {
        this.koi.sendTest(eventType);
    }

    public void sendStreamUpdate(@NonNull KoiStreamConfiguration config) {
        this.koi.sendStreamUpdate(config);
    }

    public boolean isStreamConfigurationSupported() {
        return this.streamConfigurationFeatures != null && !this.streamConfigurationFeatures.isEmpty();
    }

    /* ---------------- */
    /* Event Listeners  */
    /* ---------------- */

    @Override
    public void onSupportedFeatures(List<KoiIntegrationFeatures> features) {
        this.features = Collections.unmodifiableList(features);
        CaffeinatedApp.getInstance().getAuth().updateBridgeData();
    }

    @Override
    public void onPlatformCategories(Map<String, String> categories) {
        this.streamCategories = Collections.unmodifiableMap(categories);
        CaffeinatedApp.getInstance().getAuth().updateBridgeData();
    }

    @Override
    public void onSupportedStreamConfigurationFeatures(List<KoiStreamConfigurationFeatures> streamConfigFeatures) {
        this.streamConfigurationFeatures = Collections.unmodifiableList(streamConfigFeatures);
        CaffeinatedApp.getInstance().getAuth().updateBridgeData();
    }

    @SuppressWarnings("deprecation")
    @KoiEventHandler
    public void onUserUpdate(UserUpdateEvent e) {
        // Update the logger with the streamer's name.
        this.logger = new FastLogger(String.format("AuthInstance (%d) %s", this.tokenId.hashCode(), e.getStreamer().getDisplayname()));
        this.userData = e.getStreamer();

        if (this.roomstate == null) {
            // TODO get rid of this by broadcasting roomstates across all platforms on
            // connect. (KOI)
            this.roomstate = new RoomstateEvent(e.getStreamer());
            CaffeinatedApp.getInstance().getKoi().onEvent(this.roomstate);
        }

        CaffeinatedApp.getInstance().getAuth().updateBridgeData();
    }

    @KoiEventHandler
    public void onStreamStatus(StreamStatusEvent e) {
        this.streamData = e;
        CaffeinatedApp.getInstance().getAuth().updateBridgeData();
    }

    @KoiEventHandler
    public void onViewerList(ViewerListEvent e) {
        this.viewers = e.getViewers();
        CaffeinatedApp.getInstance().getAuth().updateBridgeData();
    }

    @KoiEventHandler
    public void onRoomState(RoomstateEvent e) {
        this.roomstate = e;
        CaffeinatedApp.getInstance().getAuth().updateBridgeData();
    }

    @KoiEventHandler
    public void onEvent(KoiEvent e) {
        KoiEventUtil.reflectInvoke(CaffeinatedApp.getInstance().getKoi(), e);
    }

    /* ---------------- */
    /* Connection Stuff */
    /* ---------------- */

    private void reconnect() {
        if (!this.disposed && !this.koi.isConnected()) {
            try {
                this.koi.login(this.token);
            } catch (Exception e) {
                this.logger.exception(e);
                new AsyncTask(() -> {
                    this.onClose(true);
                });
            }
        }
    }

    @Override
    public void onError(String errorCode) {
        switch (errorCode) {
            case "USER_AUTH_INVALID": {
                this.invalidate();
                return;
            }
        }
    }

    @SneakyThrows
    @Override
    public void onClose(boolean remote) {
        if (!this.disposed) {
            this.logger.info("Reconnecting to Koi.");
            Thread.sleep(2000);
            this.reconnect();
        }
    }

    @Override
    public void onServerMessage(String message) {
        this.logger.info("Server message: %s", message);
    }

    @Override
    public void onException(Exception e) {
        this.logger.exception(e);
    }

    @Override
    public void close() throws IOException {
        this.disposed = true;
        this.koi.close();
    }

    public boolean isConnected() {
        return this.koi.isConnected();
    }

}
