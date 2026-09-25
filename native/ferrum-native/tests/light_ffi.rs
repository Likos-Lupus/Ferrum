//! Integration tests for the `ferrum_light_block_batch` blob ABI (ADR-0018).

use ferrum::light::ferrum_light_block_batch;

const FERRUM_OK: i32 = 0;
const FERRUM_ERR_BUFFER_TOO_SMALL: i32 = -2;
const FERRUM_ERR_MALFORMED_INPUT: i32 = -3;
const FERRUM_ERR_UNSUPPORTED: i32 = -5;

const CELLS: usize = 4096;

struct Blob {
    bytes: Vec<u8>,
}

impl Blob {
    fn new() -> Self {
        Self { bytes: Vec::new() }
    }

    fn u8(&mut self, value: u8) -> &mut Self {
        self.bytes.push(value);
        self
    }

    fn u16(&mut self, value: u16) -> &mut Self {
        self.bytes.extend_from_slice(&value.to_le_bytes());
        self
    }

    fn u32(&mut self, value: u32) -> &mut Self {
        self.bytes.extend_from_slice(&value.to_le_bytes());
        self
    }

    fn i32(&mut self, value: i32) -> &mut Self {
        self.bytes.extend_from_slice(&value.to_le_bytes());
        self
    }

    fn header(
        &mut self,
        section_count: u32,
        palette_count: u32,
        decrease_count: u32,
        increase_count: u32,
        check_count: u32,
    ) -> &mut Self {
        self.bytes.extend_from_slice(b"FBLT");
        self.u8(1).u8(0).u16(0);
        self.u32(section_count)
            .u32(palette_count)
            .u32(decrease_count)
            .u32(increase_count)
            .u32(check_count)
            .u32(0)
    }

    fn property(&mut self, opacity: u8, emission: u8, empty: u8) -> &mut Self {
        self.u8(opacity).u8(emission).u8(empty).u8(0)
    }

    #[allow(clippy::too_many_arguments)]
    fn section(
        &mut self,
        x: i32,
        y: i32,
        z: i32,
        default_level: u8,
        has_data: bool,
        light_on: bool,
        props: &[u16],
        levels: &[u8],
    ) -> &mut Self {
        self.i32(x)
            .i32(y)
            .i32(z)
            .i32(0)
            .u8(default_level)
            .u8(u8::from(has_data))
            .u8(u8::from(light_on))
            .u8(0);
        for value in props {
            self.u16(*value);
        }
        if has_data {
            self.bytes.extend_from_slice(levels);
        }
        self
    }

    fn node(&mut self, x: i32, y: i32, z: i32) -> &mut Self {
        self.i32(x).i32(y).i32(z)
    }
}

fn uniform(value: u16) -> Vec<u16> {
    vec![value; CELLS]
}

fn call(input: &[u8], out_cap: usize) -> (i32, Vec<u8>, usize) {
    let mut out = vec![0u8; out_cap.max(1)];
    let mut written = 0usize;
    let status = unsafe {
        ferrum_light_block_batch(
            input.as_ptr(),
            input.len(),
            out.as_mut_ptr(),
            out.len(),
            &mut written,
        )
    };
    (status, out, written)
}

#[test]
fn empty_batch_is_ok() {
    let mut blob = Blob::new();
    blob.header(0, 0, 0, 0, 0);
    let (status, out, written) = call(&blob.bytes, 64);
    assert_eq!(status, FERRUM_OK);
    assert_eq!(written, 16);
    assert_eq!(&out[0..4], b"FBLO");
    assert_eq!(u32::from_le_bytes([out[8], out[9], out[10], out[11]]), 0);
}

#[test]
fn emission_propagates_through_the_ffi() {
    let mut blob = Blob::new();
    blob.header(1, 2, 0, 0, 1);
    blob.property(1, 0, 1); // air
    blob.property(1, 15, 1); // source
    let mut props = uniform(0);
    props[(8 << 8) | (8 << 4) | 8] = 1;
    let levels = vec![0u8; CELLS];
    blob.section(0, 0, 0, 0, true, true, &props, &levels);
    blob.node(8, 8, 8); // one check
    let (status, out, written) = call(&blob.bytes, 4096);
    assert_eq!(status, FERRUM_OK, "written={written}");
    assert_eq!(&out[0..4], b"FBLO");
    assert_eq!(u32::from_le_bytes([out[8], out[9], out[10], out[11]]), 1);
    // one changed section record; nibbles start at offset 16 + 16.
    let data = &out[32..32 + 2048];
    let nibble = |cell: usize| -> u8 {
        let byte = data[cell >> 1];
        if cell & 1 == 0 {
            byte & 15
        } else {
            (byte >> 4) & 15
        }
    };
    assert_eq!(nibble((8 << 8) | (8 << 4) | 8), 15);
    assert_eq!(nibble((8 << 8) | (8 << 4) | 9), 14);
}

#[test]
fn bad_magic_is_malformed() {
    let mut blob = Blob::new();
    blob.header(0, 0, 0, 0, 0);
    blob.bytes[0] = b'X';
    let (status, _, _) = call(&blob.bytes, 64);
    assert_eq!(status, FERRUM_ERR_MALFORMED_INPUT);
}

#[test]
fn unknown_version_is_unsupported() {
    let mut blob = Blob::new();
    blob.header(0, 0, 0, 0, 0);
    blob.bytes[4] = 9;
    let (status, _, _) = call(&blob.bytes, 64);
    assert_eq!(status, FERRUM_ERR_UNSUPPORTED);
}

#[test]
fn nonzero_flags_is_unsupported() {
    let mut blob = Blob::new();
    blob.header(0, 0, 0, 0, 0);
    blob.bytes[5] = 1;
    let (status, _, _) = call(&blob.bytes, 64);
    assert_eq!(status, FERRUM_ERR_UNSUPPORTED);
}

#[test]
fn truncated_is_malformed() {
    let mut blob = Blob::new();
    blob.header(0, 0, 0, 0, 0);
    blob.bytes.truncate(20);
    let (status, _, _) = call(&blob.bytes, 64);
    assert_eq!(status, FERRUM_ERR_MALFORMED_INPUT);
}

#[test]
fn palette_id_out_of_range_is_malformed() {
    let mut blob = Blob::new();
    blob.header(1, 1, 0, 0, 0);
    blob.property(1, 0, 1);
    let mut props = uniform(0);
    props[0] = 3; // only palette index 0 exists
    blob.section(0, 0, 0, 0, false, true, &props, &[]);
    let (status, _, _) = call(&blob.bytes, 64);
    assert_eq!(status, FERRUM_ERR_MALFORMED_INPUT);
}

#[test]
fn buffer_too_small_reports_required() {
    let mut blob = Blob::new();
    blob.header(1, 1, 0, 0, 1);
    blob.property(1, 15, 1);
    blob.section(0, 0, 0, 0, false, true, &uniform(0), &[]);
    blob.node(0, 0, 0);
    let (status, _, written) = call(&blob.bytes, 8);
    assert_eq!(status, FERRUM_ERR_BUFFER_TOO_SMALL);
    assert_eq!(written, 16 + 16 + 2048);
}
