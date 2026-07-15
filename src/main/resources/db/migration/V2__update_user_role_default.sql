-- D3: bịt lỗ hổng leo thang đặc quyền — DB default trước đây là ADMIN, phải đổi sang USER.
-- Đồng bộ với thay đổi @Builder.Default trong User.java để code và schema nhất quán.
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER';
