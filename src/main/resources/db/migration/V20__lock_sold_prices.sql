CREATE FUNCTION prevent_ticket_price_change() RETURNS trigger AS $$
BEGIN
    IF NEW.price IS DISTINCT FROM OLD.price THEN
        RAISE EXCEPTION 'Không được sửa giá của vé đã bán' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tickets_lock_price
    BEFORE UPDATE ON tickets
    FOR EACH ROW EXECUTE FUNCTION prevent_ticket_price_change();

CREATE FUNCTION prevent_booking_fnb_price_change() RETURNS trigger AS $$
BEGIN
    IF NEW.unit_price IS DISTINCT FROM OLD.unit_price THEN
        RAISE EXCEPTION 'Không được sửa đơn giá bắp nước đã chọn' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_fnb_details_lock_price
    BEFORE UPDATE ON booking_fnb_details
    FOR EACH ROW EXECUTE FUNCTION prevent_booking_fnb_price_change();

CREATE FUNCTION prevent_settled_booking_amount_change() RETURNS trigger AS $$
BEGIN
    IF OLD.status <> 'PENDING' AND (
        NEW.total_amount IS DISTINCT FROM OLD.total_amount
        OR NEW.discount_amount IS DISTINCT FROM OLD.discount_amount
        OR NEW.final_amount IS DISTINCT FROM OLD.final_amount
    ) THEN
        RAISE EXCEPTION 'Không được sửa số tiền của đơn đã chốt' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bookings_lock_amounts
    BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION prevent_settled_booking_amount_change();

CREATE FUNCTION prevent_payment_amount_change() RETURNS trigger AS $$
BEGIN
    IF NEW.amount IS DISTINCT FROM OLD.amount THEN
        RAISE EXCEPTION 'Không được sửa số tiền của giao dịch thanh toán' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payments_lock_amount
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION prevent_payment_amount_change();
