package com.financetracker.document.extraction;

/**
 * Main Responsibility: Active category choice passed into the receipt parser.
 *
 * The parser may map a slug to id; unknown slugs stay null after validation.
 */
public record CategoryOption(Long id, String name, String slug) {
}
