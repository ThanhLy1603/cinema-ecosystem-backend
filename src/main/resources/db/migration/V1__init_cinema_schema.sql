-- =============================================================================
-- CINEMA CHAIN & COMMUNITY ECOSYSTEM DATABASE SCHEMA (POSTGRESQL 16+)
-- Total: 35 Tables
-- Features: Multi-branch, Dynamic Pricing, Concurrency Safe Seat Locking, 
--           Promotions (JSONB Rules), Loyalty, Blog & Reviews, AI Vector RAG
-- =============================================================================

-- Kích hoạt extension hỗ trợ AI Search và UUID (nếu cần)
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================================================
-- PHÂN HỆ 1: NGƯỜI DÙNG, BẢO MẬT & PHÂN QUYỀN (5 BẢNG)
-- =============================================================================

-- 1. Bảng tài khoản người dùng
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(150) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) UNIQUE,
    avatar_url TEXT,
    tier VARCHAR(50) DEFAULT 'STAR', -- STAR, GOLD, DIAMOND
    loyalty_points INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, LOCKED, SUSPENDED
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bảng vai trò
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL, -- ROLE_CUSTOMER, ROLE_STAFF, ROLE_ADMIN
    name VARCHAR(100) NOT NULL,
    description TEXT
);

-- 3. Bảng trung gian tài khoản - vai trò
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 4. Bảng Refresh Token (Hỗ trợ Silent Refresh & Thu hồi phiên)
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    device_info VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- 5. Bảng hồ sơ nhân viên rạp
CREATE TABLE staff_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    employee_code VARCHAR(50) UNIQUE NOT NULL,
    position VARCHAR(100) NOT NULL, -- TICKETING_STAFF, MANAGER, AUDITOR
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- PHÂN HỆ 2: CỤM RẠP & CƠ SỞ VẬT CHẤT (6 BẢNG)
-- =============================================================================

-- 6. Bảng chi nhánh rạp
CREATE TABLE cinemas (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    city VARCHAR(100) NOT NULL,
    address TEXT NOT NULL,
    phone VARCHAR(20),
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    status VARCHAR(50) DEFAULT 'ACTIVE'
);

-- Bổ sung foreign key từ staff_profiles đến cinemas
ALTER TABLE staff_profiles ADD COLUMN cinema_id BIGINT REFERENCES cinemas(id) ON DELETE SET NULL;

-- 7. Danh mục tiện ích rạp
CREATE TABLE cinema_facilities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    icon_url TEXT
);

-- 8. Bảng trung gian rạp - tiện ích
CREATE TABLE cinema_facility_mappings (
    cinema_id BIGINT NOT NULL REFERENCES cinemas(id) ON DELETE CASCADE,
    facility_id BIGINT NOT NULL REFERENCES cinema_facilities(id) ON DELETE CASCADE,
    PRIMARY KEY (cinema_id, facility_id)
);

-- 9. Bảng phòng chiếu
CREATE TABLE halls (
    id BIGSERIAL PRIMARY KEY,
    cinema_id BIGINT NOT NULL REFERENCES cinemas(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    screen_type VARCHAR(50) DEFAULT '2D', -- 2D, 3D, IMAX, 4DX
    total_seats INT NOT NULL DEFAULT 0,
    status VARCHAR(50) DEFAULT 'ACTIVE'
);

-- 10. Bảng định nghĩa loại ghế
CREATE TABLE seat_types (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL, -- STANDARD, VIP, SWEETBOX
    name VARCHAR(100) NOT NULL,
    default_surcharge NUMERIC(12, 2) DEFAULT 0.00
);

-- 11. Bảng ghế chi tiết trong phòng chiếu
CREATE TABLE seats (
    id BIGSERIAL PRIMARY KEY,
    hall_id BIGINT NOT NULL REFERENCES halls(id) ON DELETE CASCADE,
    seat_type_id BIGINT NOT NULL REFERENCES seat_types(id),
    seat_row VARCHAR(5) NOT NULL,
    seat_number INT NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, MAINTENANCE
    CONSTRAINT uq_hall_seat UNIQUE (hall_id, seat_row, seat_number)
);
CREATE INDEX idx_seats_hall ON seats(hall_id);

-- =============================================================================
-- PHÂN HỆ 3: PHIM & DANH MỤC NỘI DUNG (5 BẢNG)
-- =============================================================================

-- 12. Bảng phim
CREATE TABLE movies (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    original_title VARCHAR(255),
    duration_minutes INT NOT NULL,
    release_date DATE NOT NULL,
    end_date DATE,
    rated VARCHAR(10) NOT NULL, -- P, C13, C16, C18
    poster_url TEXT,
    banner_url TEXT,
    trailer_url TEXT,
    description TEXT,
    status VARCHAR(50) DEFAULT 'NOW_SHOWING' -- COMING_SOON, NOW_SHOWING, ENDED
);

-- 13. Danh mục thể loại phim
CREATE TABLE genres (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL
);

-- 14. Bảng trung gian phim - thể loại
CREATE TABLE movie_genres (
    movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (movie_id, genre_id)
);

-- 15. Bảng thông tin nghệ sĩ (Đạo diễn, Diễn viên)
CREATE TABLE persons (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    avatar_url TEXT,
    bio TEXT
);

-- 16. Bảng trung gian phim - đoàn làm phim (Cast & Crew)
CREATE TABLE movie_casts (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    person_id BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    role_type VARCHAR(50) NOT NULL, -- DIRECTOR, ACTOR, PRODUCER
    character_name VARCHAR(150)
);

-- =============================================================================
-- PHÂN HỆ 4: SUẤT CHIẾU & MA TRẬN GIÁ ĐỘNG (3 BẢNG)
-- =============================================================================

-- 17. Bảng suất chiếu
CREATE TABLE showtimes (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id),
    hall_id BIGINT NOT NULL REFERENCES halls(id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    base_price NUMERIC(12, 2) NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE'
);
-- Index Composite cực kỳ quan trọng để truy vấn lịch chiếu
CREATE INDEX idx_showtimes_hall_time ON showtimes(hall_id, start_time);
CREATE INDEX idx_showtimes_movie_time ON showtimes(movie_id, start_time);

-- 18. Bảng quy tắc giá vé linh hoạt
CREATE TABLE price_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(150) NOT NULL,
    day_type VARCHAR(50) NOT NULL, -- ALL, WEEKDAY, WEEKEND, HOLIDAY
    time_from TIME,
    time_to TIME,
    surcharge_amount NUMERIC(12, 2) DEFAULT 0.00
);

-- 19. Bảng liên kết quy tắc giá cho từng suất chiếu
CREATE TABLE showtime_price_modifiers (
    showtime_id BIGINT NOT NULL REFERENCES showtimes(id) ON DELETE CASCADE,
    price_rule_id BIGINT NOT NULL REFERENCES price_rules(id) ON DELETE CASCADE,
    PRIMARY KEY (showtime_id, price_rule_id)
);

-- =============================================================================
-- PHÂN HỆ 5: ĐẶT VÉ, GIỮ GHẾ & COMBO F&B (7 BẢNG)
-- =============================================================================

-- 20. Bảng đơn đặt hàng tổng (Booking Header)
CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    booking_code VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    cinema_id BIGINT NOT NULL REFERENCES cinemas(id),
    total_amount NUMERIC(12, 2) NOT NULL,
    discount_amount NUMERIC(12, 2) DEFAULT 0.00,
    final_amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING', -- PENDING, PAID, EXPIRED, CANCELLED
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_status_created ON bookings(status, created_at);

-- 21. Bảng khóa ghế tạm thời (CHỐNG DOUBLE-BOOKING / RACE CONDITION)
CREATE TABLE seat_reservations (
    id BIGSERIAL PRIMARY KEY,
    showtime_id BIGINT NOT NULL REFERENCES showtimes(id) ON DELETE CASCADE,
    seat_id BIGINT NOT NULL REFERENCES seats(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    booking_id BIGINT REFERENCES bookings(id) ON DELETE SET NULL,
    status VARCHAR(50) DEFAULT 'HELD', -- HELD, BOOKED
    reserved_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    -- Chặn đứng 2 người giữ cùng 1 ghế tại tầng Database
    CONSTRAINT uq_showtime_seat UNIQUE (showtime_id, seat_id)
);
CREATE INDEX idx_seat_reservations_status_expires ON seat_reservations(status, expires_at);

-- 22. Bảng vé điện tử chi tiết (Đã thanh toán)
CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    showtime_id BIGINT NOT NULL REFERENCES showtimes(id),
    seat_id BIGINT NOT NULL REFERENCES seats(id),
    ticket_code VARCHAR(100) UNIQUE NOT NULL,
    qr_code_url TEXT,
    price NUMERIC(12, 2) NOT NULL,
    checked_in BOOLEAN DEFAULT FALSE,
    checked_in_at TIMESTAMPTZ
);
CREATE INDEX idx_tickets_booking ON tickets(booking_id);

-- 23. Danh mục đồ ăn/uống đơn lẻ
CREATE TABLE fnb_items (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL, -- POPCORN, DRINK, SNACK
    price NUMERIC(12, 2) NOT NULL,
    image_url TEXT,
    status VARCHAR(50) DEFAULT 'AVAILABLE'
);

-- 24. Danh mục gói combo bắp nước
CREATE TABLE fnb_combos (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    image_url TEXT,
    status VARCHAR(50) DEFAULT 'AVAILABLE'
);

-- 25. Bảng chi tiết thành phần trong combo
CREATE TABLE fnb_combo_items (
    combo_id BIGINT NOT NULL REFERENCES fnb_combos(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES fnb_items(id) ON DELETE CASCADE,
    quantity INT NOT NULL DEFAULT 1,
    PRIMARY KEY (combo_id, item_id)
);

-- 26. Bảng bắp nước đi kèm trong đơn đặt vé
CREATE TABLE booking_fnb_details (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    combo_id BIGINT REFERENCES fnb_combos(id),
    item_id BIGINT REFERENCES fnb_items(id),
    quantity INT NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    CONSTRAINT chk_booking_fnb_choice CHECK (combo_id IS NOT NULL OR item_id IS NOT NULL)
);

-- =============================================================================
-- PHÂN HỆ 6: THANH TOÁN, KHUYẾN MẠI & LOYALTY (4 BẢNG)
-- =============================================================================

-- 27. Bảng giao dịch thanh toán (Cổng VNPay, MoMo, ZaloPay)
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    payment_method VARCHAR(50) NOT NULL, -- VNPAY, MOMO, ZALOPAY, CASH
    transaction_ref VARCHAR(100) UNIQUE, -- Mã giao dịch từ cổng thanh toán
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED
    response_data JSONB, -- Lưu log callback nguyên bản để đối soát
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payments_booking ON payments(booking_id);

-- 28. Bảng chương trình khuyến mại & Voucher
CREATE TABLE promotions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE,
    title VARCHAR(255) NOT NULL,
    discount_type VARCHAR(50) NOT NULL, -- PERCENT, FIXED_AMOUNT
    discount_val NUMERIC(12, 2) NOT NULL,
    max_discount_amount NUMERIC(12, 2),
    min_order_amount NUMERIC(12, 2) DEFAULT 0.00,
    conditions JSONB, -- Điều kiện động: {"apply_days": [2], "target_tier": "GOLD", "min_tickets": 2}
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    usage_limit INT,
    used_count INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'ACTIVE'
);

-- 29. Bảng lịch sử sử dụng mã khuyến mại
CREATE TABLE promotion_usages (
    id BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    discount_applied NUMERIC(12, 2) NOT NULL,
    used_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_promotion_usages_user_promo ON promotion_usages(user_id, promotion_id);

-- 30. Bảng lịch sử tích / đổi điểm thành viên
CREATE TABLE loyalty_point_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    booking_id BIGINT REFERENCES bookings(id) ON DELETE SET NULL,
    points_change INT NOT NULL, -- Dương là tích lũy, âm là tiêu điểm
    transaction_type VARCHAR(50) NOT NULL, -- EARN, REDEEM, ADJUST
    balance_after INT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_loyalty_user ON loyalty_point_transactions(user_id);

-- =============================================================================
-- PHÂN HỆ 7: BLOG, REVIEW & CỘNG ĐỒNG (4 BẢNG)
-- =============================================================================

-- 31. Bảng đánh giá và review phim
CREATE TABLE movie_reviews (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 10),
    content TEXT,
    is_verified_buyer BOOLEAN DEFAULT FALSE,
    like_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_movie_review UNIQUE(movie_id, user_id)
);
CREATE INDEX idx_movie_reviews_movie ON movie_reviews(movie_id);

-- 32. Bảng tương tác cảm xúc bài review
CREATE TABLE review_reactions (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES movie_reviews(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reaction_type VARCHAR(20) NOT NULL, -- LIKE, DISLIKE
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_review_reaction UNIQUE(review_id, user_id)
);

-- 33. Bảng bài viết blog điện ảnh
CREATE TABLE blog_posts (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    thumbnail_url TEXT,
    summary TEXT,
    content TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'PUBLISHED', -- DRAFT, PUBLISHED, ARCHIVED
    view_count INT DEFAULT 0,
    published_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 34. Bảng bình luận bài viết (Hỗ trợ phân cấp đệ quy lồng nhau)
CREATE TABLE blog_comments (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    parent_id BIGINT REFERENCES blog_comments(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_blog_comments_post ON blog_comments(post_id);

-- =============================================================================
-- PHÂN HỆ 8: TRỢ LÝ ẢO AI & VECTOR KNOWLEDGE BASE (1 BẢNG)
-- =============================================================================

-- 35. Bảng cơ sở tri thức RAG hỗ trợ Chatbot thông minh
CREATE TABLE ai_knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(100) NOT NULL, -- CINEMA_INFO, TICKET_POLICY, FAQ, MOVIE_SYNOPSIS
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536), -- Vector chuẩn định dạng OpenAI text-embedding-3 hoặc Gemini
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
-- Tạo chỉ mục HNSW phục vụ tìm kiếm tương đồng vector siêu tốc
CREATE INDEX idx_ai_knowledge_embedding ON ai_knowledge_base USING hnsw (embedding vector_cosine_ops);

-- =============================================================================
-- SEED DATA MẪU KHỞI TẠO HỆ THỐNG
-- =============================================================================

-- Roles
INSERT INTO roles (code, name, description) VALUES
('ROLE_CUSTOMER', 'Khách hàng', 'Người dùng thông thường đặt vé và bình luận'),
('ROLE_STAFF', 'Nhân viên rạp', 'Soát vé và quản lý phòng chiếu'),
('ROLE_ADMIN', 'Quản trị viên', 'Toàn quyền cấu hình hệ thống');

-- Seat Types
INSERT INTO seat_types (code, name, default_surcharge) VALUES
('STANDARD', 'Ghế thường', 0.00),
('VIP', 'Ghế VIP', 15000.00),
('SWEETBOX', 'Ghế đôi Sweetbox', 45000.00);

-- Tiện ích
INSERT INTO cinema_facilities (name, icon_url) VALUES
('Bãi đỗ xe ô tô', 'https://example.com/icons/parking.svg'),
('Phòng chiếu IMAX', 'https://example.com/icons/imax.svg'),
('Quầy F&B tự động', 'https://example.com/icons/kiosk.svg');

-- Rạp mẫu
INSERT INTO cinemas (name, slug, city, address, phone, latitude, longitude) VALUES
('Star Cineplex Bitexco', 'star-cineplex-bitexco', 'Hồ Chí Minh', 'Tầng 3, Bitexco Financial Tower, Q1', '0281234567', 10.7715, 106.7042),
('Star Cineplex Landmark', 'star-cineplex-landmark', 'Hồ Chí Minh', 'Tầng B1, Landmark 81, Bình Thạnh', '0289876543', 10.7950, 106.7218);

-- Thể loại
INSERT INTO genres (name, slug) VALUES
('Hành Động', 'hanh-dong'),
('Khoa Học Viễn Tưởng', 'khoa-hoc-vien-tuong'),
('Kinh Dị', 'kinh-di'),
('Hoạt Hình', 'hoat-hinh');