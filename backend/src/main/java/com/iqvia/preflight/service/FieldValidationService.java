package com.iqvia.preflight.service;

import com.iqvia.preflight.model.StandardField;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Single source of truth for deciding whether a value is safe to use in matching.
 * Normalizes common Excel export quirks (numeric strings, full state names, zip+4 without hyphen).
 */
@Service
public class FieldValidationService {

    private static final Pattern ZIP = Pattern.compile("^\\d{5}(?:-\\d{4})?$");
    private static final Pattern ZIP_PLUS_FOUR = Pattern.compile("^\\d{9}$");
    private static final Pattern PERSON_NAME =
            Pattern.compile("^[\\p{L}][\\p{L}' .-]{0,59}$");
    private static final Pattern CITY =
            Pattern.compile("^[\\p{L}][\\p{L}' .-]{0,59}$");
    private static final Pattern ADDRESS =
            Pattern.compile("^(?:\\d{1,6}[A-Za-z]?\\s+[\\p{L}\\d][\\p{L}\\d .,'#/-]{2,99}|P\\.?\\s*O\\.?\\s*Box\\s+\\d+[\\p{L}\\d .,'#/-]{0,40})$");
    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE =
            Pattern.compile("^\\+?[\\d() .-]{10,20}$");

    private static final Set<String> US_STATES = new HashSet<>(Arrays.asList(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
            "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
            "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
            "DC", "PR", "VI", "GU", "AS", "MP"
    ));

    private static final Map<String, String> STATE_NAMES = buildStateNameMap();

    public boolean isValid(StandardField field, String rawValue) {
        String value = normalizeValue(field, clean(rawValue));
        if (value.isEmpty()) {
            return false;
        }

        switch (field) {
            case NPI:
                return isValidNpi(value);
            case ZIP:
                return isValidZip(value);
            case FIRST_NAME:
            case LAST_NAME:
                return PERSON_NAME.matcher(value).matches();
            case ADDRESS:
                return ADDRESS.matcher(value).matches();
            case CITY:
                return CITY.matcher(value).matches();
            case STATE:
                return isValidState(value);
            case EMAIL:
                return EMAIL.matcher(value).matches();
            case PHONE:
                return PHONE.matcher(value).matches()
                        && value.replaceAll("\\D", "").length() >= 10;
            default:
                return false;
        }
    }

    public boolean isValidNpi(String rawValue) {
        String npi = stripExcelDecimalSuffix(clean(rawValue)).replaceAll("\\D", "");
        if (npi.length() != 10) {
            return false;
        }

        String payload = "80840" + npi;
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = payload.length() - 1; i >= 0; i--) {
            int digit = payload.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    public String normalizeNpi(String rawValue) {
        return clean(rawValue).replaceAll("\\D", "");
    }

    public String validationMessage(StandardField field) {
        switch (field) {
            case NPI:
                return "Invalid NPI (must be a valid 10-digit NPI)";
            case ZIP:
                return "Invalid zip (use 12345 or 12345-6789)";
            case ADDRESS:
                return "Invalid street address";
            case STATE:
                return "Invalid US state code";
            case FIRST_NAME:
                return "Invalid first name";
            case LAST_NAME:
                return "Invalid last name";
            case CITY:
                return "Invalid city";
            case EMAIL:
                return "Invalid email";
            case PHONE:
                return "Invalid phone";
            default:
                return "Invalid value";
        }
    }

    private String normalizeValue(StandardField field, String value) {
        if (value.isEmpty()) {
            return value;
        }

        value = stripExcelDecimalSuffix(value);

        if (field == StandardField.NPI) {
            return value.replaceAll("[^0-9]", "");
        }

        if (field == StandardField.ZIP) {
            String digits = value.replaceAll("[^0-9]", "");
            if (digits.length() == 4) {
                return String.format(Locale.ROOT, "%05d", Integer.parseInt(digits));
            }
            if (digits.length() == 9) {
                return digits.substring(0, 5) + "-" + digits.substring(5);
            }
            if (digits.length() == 5) {
                return digits;
            }
            return value;
        }

        if (field == StandardField.STATE) {
            return normalizeStateToken(value);
        }

        return value;
    }

    private String stripExcelDecimalSuffix(String value) {
        if (value.endsWith(".0") && value.substring(0, value.length() - 2).matches("\\d+")) {
            return value.substring(0, value.length() - 2);
        }
        return value;
    }

    private boolean isValidZip(String value) {
        return ZIP.matcher(value).matches() || ZIP_PLUS_FOUR.matcher(value.replace("-", "")).matches();
    }

    private boolean isValidState(String value) {
        String token = normalizeStateToken(value);
        return US_STATES.contains(token);
    }

    private String normalizeStateToken(String value) {
        String trimmed = value.trim();
        if (trimmed.length() == 2) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        String mapped = STATE_NAMES.get(trimmed.toUpperCase(Locale.ROOT));
        return mapped != null ? mapped : trimmed.toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, String> buildStateNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put("ALABAMA", "AL");
        map.put("ALASKA", "AK");
        map.put("ARIZONA", "AZ");
        map.put("ARKANSAS", "AR");
        map.put("CALIFORNIA", "CA");
        map.put("COLORADO", "CO");
        map.put("CONNECTICUT", "CT");
        map.put("DELAWARE", "DE");
        map.put("FLORIDA", "FL");
        map.put("GEORGIA", "GA");
        map.put("HAWAII", "HI");
        map.put("IDAHO", "ID");
        map.put("ILLINOIS", "IL");
        map.put("INDIANA", "IN");
        map.put("IOWA", "IA");
        map.put("KANSAS", "KS");
        map.put("KENTUCKY", "KY");
        map.put("LOUISIANA", "LA");
        map.put("MAINE", "ME");
        map.put("MARYLAND", "MD");
        map.put("MASSACHUSETTS", "MA");
        map.put("MICHIGAN", "MI");
        map.put("MINNESOTA", "MN");
        map.put("MISSISSIPPI", "MS");
        map.put("MISSOURI", "MO");
        map.put("MONTANA", "MT");
        map.put("NEBRASKA", "NE");
        map.put("NEVADA", "NV");
        map.put("NEW HAMPSHIRE", "NH");
        map.put("NEW JERSEY", "NJ");
        map.put("NEW MEXICO", "NM");
        map.put("NEW YORK", "NY");
        map.put("NORTH CAROLINA", "NC");
        map.put("NORTH DAKOTA", "ND");
        map.put("OHIO", "OH");
        map.put("OKLAHOMA", "OK");
        map.put("OREGON", "OR");
        map.put("PENNSYLVANIA", "PA");
        map.put("RHODE ISLAND", "RI");
        map.put("SOUTH CAROLINA", "SC");
        map.put("SOUTH DAKOTA", "SD");
        map.put("TENNESSEE", "TN");
        map.put("TEXAS", "TX");
        map.put("UTAH", "UT");
        map.put("VERMONT", "VT");
        map.put("VIRGINIA", "VA");
        map.put("WASHINGTON", "WA");
        map.put("WEST VIRGINIA", "WV");
        map.put("WISCONSIN", "WI");
        map.put("WYOMING", "WY");
        map.put("DISTRICT OF COLUMBIA", "DC");
        map.put("PUERTO RICO", "PR");
        return map;
    }
}
