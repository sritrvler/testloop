-- The client treats expiry as housekeeping, but authenticated members must
-- still be able to invoke it without blocking Discover or joining a test.
grant execute on function public.expire_pending_commitments() to authenticated;
