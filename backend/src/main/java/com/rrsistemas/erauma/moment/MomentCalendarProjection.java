package com.rrsistemas.erauma.moment;

import java.time.LocalDate;

public interface MomentCalendarProjection {
    LocalDate getDate();
    long getCount();
}
