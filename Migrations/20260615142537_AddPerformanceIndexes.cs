using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace webapi.Migrations
{
    /// <inheritdoc />
    public partial class AddPerformanceIndexes : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            // Only add new indexes — no column type changes, no index drops
            migrationBuilder.CreateIndex(
                name: "idx_signatures_device_period",
                table: "inspection_signatures",
                columns: new[] { "device_model", "year", "month" });

            migrationBuilder.CreateIndex(
                name: "uq_results_record_item",
                table: "inspection_results",
                columns: new[] { "record_id", "item_name" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "idx_records_device_time",
                table: "inspection_records",
                columns: new[] { "device_model", "inspection_time" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "idx_signatures_device_period",
                table: "inspection_signatures");

            migrationBuilder.DropIndex(
                name: "uq_results_record_item",
                table: "inspection_results");

            migrationBuilder.DropIndex(
                name: "idx_records_device_time",
                table: "inspection_records");
        }
    }
}
