import { Box, Stack, Typography } from '@mui/material';
import { tokens } from '../theme/theme';
import type { Ticket } from '../types';

function formatEta(seconds: number | null): string {
  if (seconds === null) return '—';
  if (seconds <= 0) return 'now';
  const mins = Math.round(seconds / 60);
  if (mins < 1) return '<1 min';
  return `~${mins} min`;
}

export default function TicketCard({ ticket, highlight = false }: { ticket: Ticket; highlight?: boolean }) {
  const isServing = ticket.status === 'IN_SERVICE';

  return (
    <Box
      className="perforated"
      sx={{
        display: 'flex',
        alignItems: 'stretch',
        borderRadius: 2,
        overflow: 'hidden',
        border: `1px solid ${isServing ? tokens.mint : tokens.hairline}`,
        backgroundColor: highlight ? tokens.surfaceRaised : tokens.surface,
        boxShadow: highlight ? `0 0 0 2px ${tokens.amber}55` : 'none',
        transition: 'box-shadow 0.2s ease, border-color 0.2s ease',
      }}
    >
      <Box
        sx={{
          width: 96,
          minWidth: 96,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          py: 2,
          backgroundColor: isServing ? `${tokens.mint}1A` : 'transparent',
        }}
      >
        <Typography variant="overline" sx={{ color: tokens.inkMuted, fontSize: 10 }}>
          Ticket
        </Typography>
        <Typography className="tabular-nums" sx={{ fontSize: 28, fontWeight: 700, color: isServing ? tokens.mint : tokens.amber }}>
          {String(ticket.id).padStart(3, '0')}
        </Typography>
      </Box>

      <Stack spacing={0.5} sx={{ py: 2, px: 2.5, flex: 1, justifyContent: 'center' }}>
        <Typography sx={{ fontWeight: 600 }}>{ticket.customerName}</Typography>
        <Typography variant="body2" sx={{ color: tokens.inkMuted }}>
          {ticket.businessName}
        </Typography>
      </Stack>

      <Stack spacing={0.5} sx={{ py: 2, pr: 2.5, alignItems: 'flex-end', justifyContent: 'center', minWidth: 110 }}>
        {isServing ? (
          <Typography sx={{ color: tokens.mint, fontWeight: 700, fontSize: 13, letterSpacing: '0.06em' }}>
            NOW SERVING
          </Typography>
        ) : (
          <>
            <Typography variant="overline" sx={{ color: tokens.inkMuted, fontSize: 10 }}>
              Position
            </Typography>
            <Typography className="tabular-nums" sx={{ fontWeight: 700 }}>
              #{ticket.queuePosition ?? '—'}
            </Typography>
            <Typography className="tabular-nums" variant="body2" sx={{ color: tokens.amber }}>
              {formatEta(ticket.estimatedWaitSeconds)}
            </Typography>
          </>
        )}
      </Stack>
    </Box>
  );
}
