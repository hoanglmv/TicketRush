import { useEffect, useState, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { eventApi, bookingApi } from '../api';
import { SeatResponse, SeatStatusUpdate, ZoneResponse } from '../types';
import { useLanguage } from '../i18n';

export default function SeatSelectionPage() {
  const { id } = useParams<{ id: string }>();
  const [seats, setSeats] = useState<SeatResponse[]>([]);
  const [zones, setZones] = useState<ZoneResponse[]>([]);
  const [selectedSeat, setSelectedSeat] = useState<SeatResponse | null>(null);
  const [hoverSeat, setHoverSeat] = useState<{seat: SeatResponse, x: number, y: number} | null>(null);
  const [loading, setLoading] = useState(true);
  const [booking, setBooking] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const clientRef = useRef<Client | null>(null);
  const navigate = useNavigate();
  const { t } = useLanguage();

  // ISM states
  const [scale, setScale] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const mapRef = useRef<HTMLDivElement>(null);

  const eventId = Number(id);

  useEffect(() => {
    Promise.all([
      eventApi.seats(eventId),
      eventApi.zones(eventId)
    ]).then(([seatsRes, zonesRes]) => {
      setSeats(seatsRes.data.data || []);
      setZones(zonesRes.data.data || []);
      
      // Compute initial centering based on window width
      if (typeof window !== 'undefined') {
        setPosition({ x: 0, y: 50 });
      }
    }).catch(() => setError('Failed to load seats'))
      .finally(() => setLoading(false));
  }, [eventId]);

  useEffect(() => {
    const wsUrl = import.meta.env.VITE_API_URL
      ? `${import.meta.env.VITE_API_URL.replace(/\/+$/, '')}/ws`
      : '/ws';

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/event/${eventId}/seats`, (message) => {
          const update: SeatStatusUpdate = JSON.parse(message.body);
          setSeats(prev => prev.map(s =>
            s.id === update.seatId ? { ...s, status: update.status } : s
          ));
          setSelectedSeat(prev =>
            prev && prev.id === update.seatId && update.status !== 'AVAILABLE' ? null : prev
          );
        });
      },
    });
    client.activate();
    clientRef.current = client;
    return () => { client.deactivate(); };
  }, [eventId]);

  const handleSeatClick = useCallback((seat: SeatResponse, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    if (seat.status !== 'AVAILABLE') return;
    setSelectedSeat(prev => prev?.id === seat.id ? null : seat);
    setError('');
    setSuccess('');
  }, []);

  const handleLockSeat = async () => {
    if (!selectedSeat) return;
    setBooking(true);
    setError('');
    try {
      const res = await bookingApi.lockSeat(selectedSeat.id);
      setSuccess(t('seats.seatHeld'));
      const ticketId = res.data.data.id;
      setTimeout(() => navigate(`/checkout/${ticketId}`), 1500);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to lock seat');
      setSelectedSeat(null);
    } finally {
      setBooking(false);
    }
  };

  // Drag and Zoom handlers
  const handleZoomIn = () => setScale(s => Math.min(s + 0.2, 3));
  const handleZoomOut = () => setScale(s => Math.max(s - 0.2, 0.4));
  const handleResetZoom = () => { setScale(1); setPosition({ x: 0, y: 50 }); };
  
  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    if (e.deltaY < 0) handleZoomIn();
    else handleZoomOut();
  };
  const handleMouseDown = (e: React.MouseEvent) => {
    setIsDragging(true);
    setDragStart({ x: e.clientX - position.x, y: e.clientY - position.y });
  };
  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return;
    setPosition({
      x: e.clientX - dragStart.x,
      y: e.clientY - dragStart.y
    });
  };
  const handleMouseUp = () => setIsDragging(false);

  const seatsByZone = zones.map(zone => ({
    zone,
    rows: Array.from(
      new Set(seats.filter(s => s.zoneId === zone.id).map(s => s.rowNumber))
    ).sort((a, b) => a - b).map(rowNum => ({
      rowNum,
      rowLabel: String.fromCharCode(64 + rowNum),
      seats: seats.filter(s => s.zoneId === zone.id && s.rowNumber === rowNum)
        .sort((a, b) => a.colNumber - b.colNumber),
    }))
  }));

  if (loading) return <div className="flex-1 pt-20"><div className="container mx-auto px-6 flex justify-center"><div className="w-10 h-10 border-4 border-gray-200 border-t-blue-600 rounded-full animate-spin" /></div></div>;

  return (
    <div className="flex-1 pt-2.5 text-gray-900">
      <div className="container mx-auto px-6 max-w-[1400px]">
        
        {/* Top Header */}
        <div className="flex justify-between items-center mb-4 bg-white p-4 rounded-lg border border-gray-200 shadow-sm">
          <div>
            <h1 className="text-2xl font-extrabold">{t('seats.title')}</h1>
            <p className="text-gray-500 text-sm mt-1">{t('seats.subtitle')}</p>
          </div>
          {selectedSeat && (
            <div className="flex items-center gap-4 bg-gray-50 p-3 px-5 rounded-lg border border-gray-200">
              <div>
                <div className="text-xs text-gray-500">{t('seats.selected')}</div>
                <div className="font-extrabold text-black">{selectedSeat.zoneName} - Sec {selectedSeat.label}</div>
              </div>
              <div className="font-black text-2xl text-blue-600 min-w-[100px] text-right">
                {selectedSeat.price.toLocaleString('vi-VN')}₫
              </div>
              <button onClick={handleLockSeat} disabled={booking} className="bg-blue-600 text-white px-6 py-3 rounded-md text-base font-bold hover:bg-blue-700 transition-colors disabled:opacity-50 shadow-sm">
                {booking ? t('seats.holding') : t('seats.buyNow')}
              </button>
            </div>
          )}
        </div>

        {error && <div className="bg-red-100 text-red-700 px-4 py-3 rounded mb-4 font-bold">{error}</div>}
        {success && <div className="bg-green-100 text-green-700 px-4 py-3 rounded mb-4 font-bold">{success}</div>}

        {/* ISM (Interactive Seat Map) Container */}
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-5">
          
          <div 
            className="w-full h-[700px] bg-gray-50 rounded-lg overflow-hidden relative border border-gray-200 cursor-grab active:cursor-grabbing select-none"
            onWheel={handleWheel}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
            ref={mapRef}
          >
            <div 
              className="w-full h-full origin-top-left will-change-transform flex flex-col items-center justify-center absolute"
              style={{ 
                transform: `translate(${position.x}px, ${position.y}px) scale(${scale})`
              }}
            >
              <div className="w-[400px] h-[80px] bg-gray-300 rounded-b-[200px] flex items-center justify-center font-extrabold text-gray-600 text-xl mb-[60px] border-2 border-gray-400 shadow-sm">{t('seats.mainStage')}</div>

              {seatsByZone.map(({ zone, rows }) => (
                <div key={zone.id} className="relative mt-8">
                  {/* Floating Zone Name */}
                  <div className="absolute -top-4 -right-10 text-sm font-extrabold opacity-80 uppercase" style={{ color: zone.color }}>
                    {zone.name}
                  </div>
                  
                  {rows.map(({ rowNum, rowLabel, seats: rowSeats }) => (
                    <div className="flex items-center justify-center gap-1.5 mb-1.5" key={rowNum}>
                      <span className="w-6 text-center text-xs text-gray-500 font-bold">{rowLabel}</span>
                      {rowSeats.map(seat => (
                        <button
                          key={seat.id}
                          className={`w-7 h-7 rounded-full border border-black/10 flex items-center justify-center text-[10px] font-bold text-white transition-transform duration-100 ${
                            selectedSeat?.id === seat.id ? 'bg-green-500 border-2 border-white shadow-[0_0_0_2px_#22c55e] scale-125 z-10' :
                            seat.status === 'AVAILABLE' ? 'hover:scale-125 hover:shadow-md hover:z-10' :
                            seat.status === 'LOCKED' ? 'bg-gray-400 cursor-not-allowed' : 'bg-gray-200 border-none text-transparent cursor-not-allowed'
                          }`}
                          style={{
                            background: seat.status === 'AVAILABLE' ? zone.color : undefined
                          }}
                          onClick={(e) => handleSeatClick(seat, e)}
                          onMouseEnter={(e) => {
                            if (seat.status === 'AVAILABLE') {
                              setHoverSeat({ seat, x: e.clientX, y: e.clientY });
                            }
                          }}
                          onMouseLeave={() => setHoverSeat(null)}
                          disabled={seat.status !== 'AVAILABLE'}
                        >
                          {seat.colNumber}
                        </button>
                      ))}
                      <span className="w-6 text-center text-xs text-gray-500 font-bold">{rowLabel}</span>
                    </div>
                  ))}
                </div>
              ))}
            </div>

            <div className="absolute right-5 bottom-5 flex flex-col gap-2 z-10">
              <button className="w-10 h-10 bg-white border border-gray-300 rounded-full flex items-center justify-center shadow-md hover:bg-gray-50 text-xl font-bold" onClick={handleZoomIn}>+</button>
              <button className="w-10 h-10 bg-white border border-gray-300 rounded-full flex items-center justify-center shadow-md hover:bg-gray-50 text-xl font-bold" onClick={handleZoomOut}>−</button>
              <button className="w-10 h-10 bg-white border border-gray-300 rounded-full flex items-center justify-center shadow-md hover:bg-gray-50 text-lg font-bold" onClick={handleResetZoom}>↺</button>
            </div>
          </div>

          {/* Sidebar Legend */}
          <div>
            <div className="bg-white p-5 rounded-lg border border-gray-200 shadow-sm">
              <h3 className="text-base font-extrabold mb-4">{t('seats.filterByPrice')}</h3>
              <div className="flex flex-col gap-2">
                {zones.sort((a, b) => b.price - a.price).map(z => (
                  <div key={z.id} className="flex justify-between items-center p-2 border-b border-gray-100">
                    <div className="flex items-center gap-2">
                      <div className="w-4 h-4 rounded" style={{ background: z.color }}></div>
                      <span className="text-sm font-bold text-gray-700">{z.name}</span>
                    </div>
                    <span className="text-sm font-extrabold">{z.price.toLocaleString('vi-VN')}₫</span>
                  </div>
                ))}
              </div>

              <h3 className="text-base font-extrabold mt-6 mb-4">{t('seats.statusColors')}</h3>
              <div className="flex flex-col gap-3">
                <div className="flex items-center gap-2 text-sm text-gray-600 font-medium">
                  <div className="w-4 h-4 rounded-full bg-green-500" />
                  <span>{t('seats.selectedStatus')}</span>
                </div>
                <div className="flex items-center gap-2 text-sm text-gray-600 font-medium">
                  <div className="w-4 h-4 rounded-full bg-gray-400" />
                  <span>{t('seats.lockedStatus')}</span>
                </div>
                <div className="flex items-center gap-2 text-sm text-gray-600 font-medium">
                  <div className="w-4 h-4 rounded-full bg-gray-200" />
                  <span>{t('seats.soldOut')}</span>
                </div>
              </div>
            </div>
          </div>

        </div>

      </div>

      {/* Ticketmaster Style Hover Tooltip */}
      {hoverSeat && (
        <div className="fixed bg-white border border-gray-300 shadow-xl p-3 rounded-lg pointer-events-none z-[9999] min-w-[150px]" style={{ left: hoverSeat.x + 15, top: hoverSeat.y - 15 }}>
          <div className="flex justify-between items-center mb-2">
            <span className="text-[11px] font-extrabold text-gray-500 uppercase">{t('seats.standardTicket')}</span>
          </div>
          <div className="font-extrabold text-[15px] mb-1 text-black">Sec {hoverSeat.seat.zoneName}, Row {hoverSeat.seat.label.replace(/[0-9]/g, '')}, Seat {hoverSeat.seat.colNumber}</div>
          <div className="font-bold text-blue-600 text-lg">{hoverSeat.seat.price.toLocaleString('vi-VN')}₫</div>
          <div className="text-xs text-gray-500 mt-1">{t('seats.feesTaxes')}</div>
        </div>
      )}
    </div>
  );
}
