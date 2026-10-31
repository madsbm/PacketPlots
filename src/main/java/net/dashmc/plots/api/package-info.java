/**
 * Tools for other plugins, which PacketPlots itself doesn't use: pasting a
 * virtual chunk into the real world, and building render pipelines that change
 * how environments look, e.g. with a backdrop.
 *
 * Other plugins reach the rest of PacketPlots through the static getters of
 * PacketPlots, such as its EnvironmentManager, and listen to the events of
 * net.dashmc.plots.events. An environment's chunks, for the paster and the
 * pipelines, come from IEnvironment#getChunks.
 */
package net.dashmc.plots.api;
