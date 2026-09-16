import test from "node:test";
import assert from "node:assert/strict";

import {
    createDestinationKey,
    getResizeOptions,
    isSupportedImage,
} from "./index.mjs";

test("creates an optimized WebP key for a product JPEG", () => {
    assert.equal(
        createDestinationKey(
            "incoming/products/100/image-id.jpg",
        ),
        "optimized/products/100/image-id.webp",
    );
});

test("creates an optimized WebP key for a seller-profile PNG", () => {
    assert.equal(
        createDestinationKey(
            "incoming/seller-profiles/15/profile-id.png",
        ),
        "optimized/seller-profiles/15/profile-id.webp",
    );
});

test("preserves dots and replaces only the extension", () => {
    assert.equal(
        createDestinationKey(
            "incoming/products/100/summer.sale.v2.jpeg",
        ),
        "optimized/products/100/summer.sale.v2.webp",
    );
});

test("rejects a key outside the incoming prefix", () => {
    assert.throws(
        () => createDestinationKey("products/100/image.jpg"),
        /incoming\//,
    );
});

test("accepts supported image extensions regardless of case", () => {
    assert.equal(
        isSupportedImage("incoming/products/1/image.jpg"),
        true,
    );

    assert.equal(
        isSupportedImage("incoming/products/1/image.JPEG"),
        true,
    );

    assert.equal(
        isSupportedImage("incoming/products/1/image.png"),
        true,
    );

    assert.equal(
        isSupportedImage("incoming/products/1/image.WEBP"),
        true,
    );
});

test("rejects unsupported or misleading extensions", () => {
    assert.equal(
        isSupportedImage("incoming/products/1/image.gif"),
        false,
    );

    assert.equal(
        isSupportedImage("incoming/products/1/image.svg"),
        false,
    );

    assert.equal(
        isSupportedImage(
            "incoming/products/1/image.jpg.exe",
        ),
        false,
    );

    assert.equal(
        isSupportedImage("incoming/products/1/no-extension"),
        false,
    );
});

test("uses a square cover resize for seller profiles", () => {
    assert.deepEqual(
        getResizeOptions(
            "incoming/seller-profiles/15/profile.png",
        ),
        {
            width: 512,
            height: 512,
            fit: "cover",
            position: "centre",
            withoutEnlargement: true,
        },
    );
});

test("uses aspect-ratio-preserving resize for products", () => {
    assert.deepEqual(
        getResizeOptions(
            "incoming/products/100/product.jpg",
        ),
        {
            width: 1200,
            height: 1200,
            fit: "inside",
            withoutEnlargement: true,
        },
    );
});

test("returns null for an unknown incoming directory", () => {
    assert.equal(
        getResizeOptions(
            "incoming/unknown/1/image.png",
        ),
        null,
    );
});