package com.iam.authcore.repository;

import com.iam.authcore.entity.DeviceCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.Optional;

public interface DeviceCodeRepository extends JpaRepository<DeviceCode, String> {
    Optional<DeviceCode> findByUserCode(String userCode);
    Optional<DeviceCode> findByDeviceCodeAndStatus(String deviceCode, DeviceCode.Status status);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM DeviceCode d WHERE d.expiresAt < :now")
    int deleteExpired(Instant now);
}
