CREATE TABLE pet_profile (
    id CHAR(36) NOT NULL,
    name VARCHAR(80) NOT NULL,
    pet_type VARCHAR(30) NOT NULL,
    breed VARCHAR(100) NULL,
    age_months INT NULL,
    weight_kg DECIMAL(6, 2) NULL,
    notes VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_pet_profile_created (created_at DESC),
    CONSTRAINT chk_pet_profile_age CHECK (age_months IS NULL OR age_months BETWEEN 0 AND 600),
    CONSTRAINT chk_pet_profile_weight CHECK (weight_kg IS NULL OR weight_kg > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
