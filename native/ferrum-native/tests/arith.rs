use ferrum::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_LIMIT_EXCEEDED};
use ferrum::arith::{checked_add, checked_mul, checked_range};

#[test]
fn checked_mul_reports_overflow() {
    assert_eq!(checked_mul(4, 5), Ok(20));
    assert_eq!(checked_mul(usize::MAX, 2), Err(FERRUM_ERR_LIMIT_EXCEEDED));
}

#[test]
fn checked_add_reports_overflow() {
    assert_eq!(checked_add(4, 5), Ok(9));
    assert_eq!(checked_add(usize::MAX, 1), Err(FERRUM_ERR_LIMIT_EXCEEDED));
}

#[test]
fn checked_range_validates_capacity() {
    assert_eq!(checked_range(2, 3, 5), Ok(()));
    assert_eq!(checked_range(3, 3, 5), Err(FERRUM_ERR_BUFFER_TOO_SMALL));
    assert_eq!(
        checked_range(usize::MAX, 1, usize::MAX),
        Err(FERRUM_ERR_LIMIT_EXCEEDED)
    );
}
