from app.config import SidecarSettings
from app.ocr.engine import TesseractOcrEngine


def test_candidate_scoring_prefers_valid_td3_check_digits() -> None:
    engine = TesseractOcrEngine(SidecarSettings())
    valid = [
        "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
        "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
    ]
    invalid = [
        "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
        "L898902C36UTO7508122F1204159ZE184226B<<<<<10",
    ]

    assert engine._mrz_shape_score(valid) > engine._mrz_shape_score(invalid)


def test_candidate_windows_score_contiguous_mrz_above_noise() -> None:
    engine = TesseractOcrEngine(SidecarSettings())
    valid = [
        "P<NGAOKONKWO<<CHUKWUEMEKA<JOHN<<<<<<<<<<<<<<",
        "A123456784NGA9008157M2908144<<<<<<<<<<<<<<06",
    ]
    candidates = engine._candidate_windows(["SA", *valid, "1"])

    best = max(candidates, key=lambda candidate: candidate.score)

    assert best.lines == valid


def test_candidate_scoring_handles_td1_layout_and_check_digits() -> None:
    engine = TesseractOcrEngine(SidecarSettings())
    valid = [
        "I<UTOD231458907<<<<<<<<<<<<<<<",
        "7408122F1204159UTO<<<<<<<<<<<6",
        "ERIKSSON<<ANNA<MARIA<<<<<<<<<<",
    ]
    invalid = [
        "I<UTOD231458807<<<<<<<<<<<<<<<",
        "7408122F1204159UTO<<<<<<<<<<<6",
        "ERIKSSON<<ANNA<MARIA<<<<<<<<<<",
    ]

    assert engine._mrz_shape_score(valid) > engine._mrz_shape_score(invalid)


def test_candidate_scoring_prefers_alphabetic_td3_state_codes() -> None:
    engine = TesseractOcrEngine(SidecarSettings())
    valid_state_code = [
        "PPGBRUK<SPECIMEN<<ANGELA<ZOE<<<<<<<<<<<<<<<<",
        "9993137658GBR9501016F3511303<<<<<<<<<<<<<<00",
    ]
    digit_confused_state_code = [
        "PPGBRUK<SPECIMEN<<ANGELA<ZOE<<<<<<<<<<<<<<<<",
        "999313765868R9501016F3511303<<<<<<<<<<<<<<00",
    ]

    assert engine._mrz_shape_score(valid_state_code) > engine._mrz_shape_score(digit_confused_state_code)


def test_best_lines_considers_multiple_source_crops(monkeypatch) -> None:
    engine = TesseractOcrEngine(SidecarSettings(tesseract_call_timeout_seconds=0.01))
    calls: list[str] = []

    def fake_candidate_crops(crop):
        return [crop]

    def fake_image_to_string(crop, _config, _started):
        calls.append(crop)
        if crop == "thresholded":
            return "\n".join(
                [
                    "PPGBRUK<SPECIKEN<<ANGELA<ZOE<<<<<<<<<<<<<<<<",
                    "999313765868R9501016F3511303<<<<<<<<<<<<<<00",
                ]
            )
        return "\n".join(
            [
                "PPGBRUK<SPECIMEN<<ANGELA<ZOE<<<<<<<<<<<<<<<<",
                "9993137658GBR9501016F3511303<<<<<<<<<<<<<<00",
            ]
        )

    monkeypatch.setattr(engine, "_candidate_crops", fake_candidate_crops)
    monkeypatch.setattr(engine, "_candidate_configs", lambda: ["config"])
    monkeypatch.setattr(engine, "_image_to_string", fake_image_to_string)

    assert engine._best_lines(["thresholded", "gray"]) == [
        "PPGBRUK<SPECIMEN<<ANGELA<ZOE<<<<<<<<<<<<<<<<",
        "9993137658GBR9501016F3511303<<<<<<<<<<<<<<00",
    ]
    assert calls == ["thresholded", "gray"]


def test_best_lines_prefers_fast_line_crop_ocr(monkeypatch) -> None:
    engine = TesseractOcrEngine(SidecarSettings(tesseract_call_timeout_seconds=0.01))

    monkeypatch.setattr(engine, "_mrz_line_crops", lambda _crop: ["line1", "line2"])
    monkeypatch.setattr(
        engine,
        "_image_to_string",
        lambda crop, _config, _started: {
            "line1": "P<LVAAPINIS<<AIVARS<<<<<<<<<<<<<<<<<<<<<<<<<",
            "line2": "LV09538247LVA8309101M2604114100983<16255<<06",
        }.get(crop, ""),
    )

    assert engine._best_lines(object()) == [
        "P<LVAAPINIS<<AIVARS<<<<<<<<<<<<<<<<<<<<<<<<<",
        "LV09538247LVA8309101M2604114100983<16255<<06",
    ]


def test_candidate_scoring_penalizes_noisy_name_line_without_separator() -> None:
    engine = TesseractOcrEngine(SidecarSettings())
    clean_name_line = [
        "PPLIESPECIMEN<<SOPHIA<<<<<<<<<<<<<<<<<<<<<<<",
        "PP00000033LIE9001011F3602025<<<<<<<<<<<<<<<2",
    ]
    noisy_name_line = [
        "PPLIESFECIMENSSOUFNIASSSSSSSS<SS<S<KS<S<S8",
        "PP00000033LIE9001011F3602025<<<<<<<<<<<<<<<2",
    ]

    assert engine._mrz_shape_score(clean_name_line) > engine._mrz_shape_score(noisy_name_line)


def test_best_lines_skips_tesseract_timeouts(monkeypatch) -> None:
    engine = TesseractOcrEngine(SidecarSettings(tesseract_call_timeout_seconds=0.01))
    calls = {"count": 0}

    def fake_image_to_string(_crop, _config, _started):
        calls["count"] += 1
        if calls["count"] == 1:
            return ""
        return "\n".join(
            [
                "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
                "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
            ]
        )

    monkeypatch.setattr(engine, "_candidate_crops", lambda crop: [crop, crop])
    monkeypatch.setattr(engine, "_candidate_configs", lambda: ["slow", "good"])
    monkeypatch.setattr(engine, "_image_to_string", fake_image_to_string)

    assert engine._best_lines(object()) == [
        "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<",
        "L898902C36UTO7408122F1204159ZE184226B<<<<<10",
    ]


def test_confidence_returns_zero_when_tesseract_times_out(monkeypatch) -> None:
    import pytesseract

    engine = TesseractOcrEngine(SidecarSettings(tesseract_call_timeout_seconds=0.01))

    def raise_timeout(*_args, **_kwargs):
        raise RuntimeError("tesseract timeout")

    monkeypatch.setattr(pytesseract, "image_to_data", raise_timeout)

    assert engine._confidence(object()) == 0.0


def test_confidence_falls_back_to_structural_score_when_tesseract_times_out(monkeypatch) -> None:
    import pytesseract

    engine = TesseractOcrEngine(SidecarSettings(tesseract_call_timeout_seconds=0.01))

    def raise_timeout(*_args, **_kwargs):
        raise RuntimeError("tesseract timeout")

    monkeypatch.setattr(pytesseract, "image_to_data", raise_timeout)

    assert engine._confidence(
        object(),
        [
            "PPGBRUK<SPECIMEN<<ANGELA<ZOE<<<<<<<<<<<<<<<<",
            "9993137658GBR9501016F3511303<<<<<<<<<<<<<<00",
        ],
    ) > 0.0
