package org.springframework.samples.petclinic.repository;

/**
 * Optional filters for an owner search. A {@code null} field means "don't filter on it".
 *
 * @param lastName prefix of the owner's last name
 * @param city     exact city
 */
public record OwnerSearchCriteria(String lastName, String city) {

    /**
     * Trims both filters and treats blank values as absent.
     */
    public OwnerSearchCriteria normalized() {
        return new OwnerSearchCriteria(blankToNull(lastName), blankToNull(city));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
