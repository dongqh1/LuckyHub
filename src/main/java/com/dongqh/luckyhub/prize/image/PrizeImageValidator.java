package com.dongqh.luckyhub.prize.image;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.prize.enums.PrizeErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

@Component
public class PrizeImageValidator {

    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(PrizeErrorCode.IMAGE_EMPTY);
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(PrizeErrorCode.IMAGE_TOO_LARGE);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(PrizeErrorCode.OSS_UPLOAD_FAILED);
        }
        if (content.length > MAX_IMAGE_BYTES) {
            throw new BusinessException(PrizeErrorCode.IMAGE_TOO_LARGE);
        }
        ImageFormat format = detect(content);
        if (format == null || !format.contentType.equals(file.getContentType())) {
            throw new BusinessException(PrizeErrorCode.IMAGE_TYPE_UNSUPPORTED);
        }
        return new ValidatedImage(content, format.contentType, format.extension);
    }

    private ImageFormat detect(byte[] content) {
        if (startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return ImageFormat.JPEG;
        }
        if (startsWith(content, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        })) {
            return ImageFormat.PNG;
        }
        if (content.length >= 12
                && Arrays.equals(Arrays.copyOfRange(content, 0, 4), new byte[]{'R', 'I', 'F', 'F'})
                && Arrays.equals(Arrays.copyOfRange(content, 8, 12), new byte[]{'W', 'E', 'B', 'P'})) {
            return ImageFormat.WEBP;
        }
        return null;
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length
                && Arrays.equals(Arrays.copyOf(content, signature.length), signature);
    }

    private enum ImageFormat {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageFormat(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }
}
