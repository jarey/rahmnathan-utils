module com.github.rahmnathan.video.converter {
    requires ffmpeg;
    requires commons.lang3;
    requires java.logging;
    exports com.github.rahmnathan.video.converter;
    exports com.github.rahmnathan.video.control;
    exports com.github.rahmnathan.video.codec;
    exports com.github.rahmnathan.video.job;
}