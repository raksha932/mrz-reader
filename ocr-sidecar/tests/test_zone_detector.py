from app.detection.zone_detector import MrzZoneDetector


def test_expanded_bbox_pulls_neighboring_mrz_rows_into_single_line_detection() -> None:
    detector = MrzZoneDetector()

    bbox = detector._expanded_bbox(image_width=1200, image_height=760, x=100, y=625, w=980, h=32)

    assert bbox.x == 81
    assert bbox.width == 1018
    assert bbox.y <= 574
    assert bbox.height >= 134


def test_expanded_bbox_stays_inside_image() -> None:
    detector = MrzZoneDetector()

    bbox = detector._expanded_bbox(image_width=300, image_height=180, x=4, y=160, w=280, h=18)

    assert bbox.x == 0
    assert bbox.y >= 0
    assert bbox.x + bbox.width <= 300
    assert bbox.y + bbox.height <= 180


def test_document_bottom_band_uses_document_coordinates_not_full_photo() -> None:
    detector = MrzZoneDetector()

    bbox = detector._document_bottom_band_bbox(
        image_width=900,
        image_height=1600,
        document_bbox=(70, 630, 790, 600),
    )

    assert bbox.x == 85
    assert bbox.width == 760
    assert bbox.y == 1038
    assert bbox.height == 180


def test_mrz_candidate_is_checked_against_document_bottom_band() -> None:
    detector = MrzZoneDetector()
    document_bbox = (70, 630, 790, 600)

    assert detector._is_in_mrz_area(900, 1600, 100, 1085, 720, 28, document_bbox)
    assert not detector._is_in_mrz_area(900, 1600, 100, 760, 720, 28, document_bbox)


def test_related_document_components_can_be_merged_across_creases() -> None:
    detector = MrzZoneDetector()
    primary = (60, 820, 760, 250)
    mrz_strip = (70, 1110, 740, 120)

    assert detector._belongs_to_document_component(primary, mrz_strip, image_height=1600)
    assert detector._union_bbox([primary, mrz_strip]) == (60, 820, 760, 410)
