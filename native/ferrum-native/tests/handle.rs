use ferrum::handle::HandleRegistry;

#[test]
fn create_use_destroy_once() {
    let registry: HandleRegistry<u32> = HandleRegistry::new();
    let handle = registry.create(7);

    assert_ne!(handle, 0);
    assert_eq!(registry.with(handle, |value| *value), Some(7));
    assert!(registry.destroy(handle));
    assert_eq!(registry.with(handle, |value| *value), None);
    assert!(!registry.destroy(handle));
    assert!(registry.is_empty());
}

#[test]
fn handles_are_distinct() {
    let registry: HandleRegistry<u8> = HandleRegistry::new();
    let first = registry.create(1);
    let second = registry.create(2);

    assert_ne!(first, second);
    assert_eq!(registry.len(), 2);
}

#[test]
fn foreign_handles_are_rejected() {
    let registry: HandleRegistry<u8> = HandleRegistry::new();

    assert_eq!(registry.with(0, |value| *value), None);
    assert_eq!(registry.with(0xDEAD_BEEF, |value| *value), None);
    assert!(!registry.destroy(0));
    assert!(!registry.destroy(0xDEAD_BEEF));
}
