package com.okaynow.common.geo;

import com.okaynow.common.exception.BadRequestException;
import com.okaynow.config.ServiceRegionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceRegionServiceTest {

    private ServiceRegionService service;

    @BeforeEach
    void setUp() {
        service = new ServiceRegionService(new ServiceRegionProperties());
    }

    @Test
    void acceptsMassachusettsZip() {
        var result = service.validate("ma", "02108");
        assertThat(result.state()).isEqualTo("MA");
        assertThat(result.zip()).isEqualTo("02108");
    }

    @Test
    void rejectsOtherState() {
        assertThatThrownBy(() -> service.validate("NY", "10001"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("operates in");
    }

    @Test
    void rejectsNonMassachusettsZip() {
        assertThatThrownBy(() -> service.validate("MA", "02903"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ZIP");
    }
}
