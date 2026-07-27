package com.dongqh.luckyhub.prize.image;

import com.dongqh.luckyhub.prize.config.OssProperties;
import com.dongqh.luckyhub.prize.storage.ObjectStorageGateway;
import com.dongqh.luckyhub.prize.vo.ImageUploadView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class PrizeImageService {

    private static final DateTimeFormatter PATH_DATE = DateTimeFormatter.ofPattern("yyyy/MM");

    private final PrizeImageValidator validator;
    private final ObjectStorageGateway gateway;
    private final OssProperties properties;
    private final Clock clock;
    private final Supplier<UUID> uuidSupplier;

    @Autowired
    public PrizeImageService(
            PrizeImageValidator validator,
            ObjectStorageGateway gateway,
            OssProperties properties
    ) {
        this(validator, gateway, properties, Clock.systemUTC(), UUID::randomUUID);
    }

    PrizeImageService(
            PrizeImageValidator validator,
            ObjectStorageGateway gateway,
            OssProperties properties,
            Clock clock,
            Supplier<UUID> uuidSupplier
    ) {
        this.validator = validator;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
        this.uuidSupplier = uuidSupplier;
    }

    public ImageUploadView upload(MultipartFile file) {
        ValidatedImage image = validator.validate(file);
        String objectKey = "prizes/%s/%s.%s".formatted(
                LocalDate.now(clock).format(PATH_DATE),
                uuidSupplier.get(),
                image.extension()
        );
        gateway.put(objectKey, image.content(), image.contentType());
        String url = properties.normalizedPublicBaseUrl() + "/" + objectKey;
        return new ImageUploadView(url, objectKey);
    }
}
